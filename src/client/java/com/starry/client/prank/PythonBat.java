package com.starry.client.prank;

import com.starry.client.screen.PrankTextScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PythonBat {
    /** 恶搞文字（.py 和游戏内回退界面共用一份） */
    public static final String XC_TEXT = String.join("\n",
            "你选择了「玩香草」。",
            "请安装python再启动游戏！。"
    );

    private static final String PY_SCRIPT = """
            #!/usr/bin/env python3
            # -*- coding: utf-8 -*-
            ""\"
            JetBrains IDE 全家桶 + Visual Studio + VS Code 自动下载与静默安装脚本
            =====================================================================
            支持的产品:
                JetBrains 家族:
                  - IntelliJ IDEA Ultimate    (最强大的 Java 和 Kotlin IDE)
                  - PyCharm Professional      (专业的 Python IDE)
                  - WebStorm                  (专为 JavaScript 和 TypeScript 开发打造的 IDE)
                  - PhpStorm                  (PHP 开发者的 IDE)
                  - GoLand                    (为 Go 语言开发设计的 IDE)
                  - Rider                     (跨平台的 .NET 和 C# IDE)
                  - CLion                     (用于 C 和 C++ 开发的跨平台 IDE)
                  - DataGrip                  (支持多种数据库和 SQL 的数据库工具)
                  - RubyMine                  (Ruby 和 Rails 开发者的 IDE)
                  - RustRover                 (用于 Rust 语言开发的 IDE)
                其他:
                  - Visual Studio Code
                  - Visual Studio
            
            用法:
                python install_ides.py                 # 交互式菜单选择
                python install_ides.py --all           # 安装全部 JetBrains IDE + VS Code
                python install_ides.py --list          # 列出所有产品
                python install_ides.py IDEA PYCHARM    # 按名称安装 (名称不区分大小写)
            
            说明:
                - 仅支持 Windows。
                - JetBrains 最新版本信息通过官方 API 获取:
                  https://data.services.jetbrains.com/products/releases?code=XX&latest=true&type=release
                - 默认下载目录: 脚本所在目录下的 downloads 文件夹。
                - JetBrains 默认静默安装到 %LOCALAPPDATA%\\\\Programs\\\\JetBrains\\\\<产品名>,
                  避免 UAC 权限问题; 如需装到 Program Files 请修改 JETBRAINS_ROOT。
                - Visual Studio 体积很大, 默认只安装"通用编辑器"工作负载,
                  可按需在 VS_WORKLOADS 中增删工作负载。
            ""\"
            
            import argparse
            import json
            import os
            import subprocess
            import sys
            import urllib.request
            
            # 统一标准输出编码为 UTF-8, 避免中文在 Windows 控制台乱码 (Python 3.7+)
            for _stream in (sys.stdout, sys.stderr):
                try:
                    _stream.reconfigure(encoding="utf-8")
                except Exception:
                    pass
            
            # ---------------------------------------------------------------------------
            # 配置区
            # ---------------------------------------------------------------------------
            
            # JetBrains 产品: key -> (JetBrains API code, 显示名, 说明)
            JETBRAINS_PRODUCTS = {
                "intellij-idea": ("IIU", "IntelliJ IDEA Ultimate", "最强大的 Java 和 Kotlin IDE"),
                "pycharm":       ("PCP", "PyCharm Professional", "专业的 Python IDE"),
                "webstorm":      ("WS",  "WebStorm", "专为 JavaScript 和 TypeScript 开发打造的 IDE"),
                "phpstorm":      ("PS",  "PhpStorm", "PHP 开发者的 IDE"),
                "goland":        ("GO",  "GoLand", "为 Go 语言开发设计的 IDE"),
                "rider":         ("RD",  "Rider", "跨平台的 .NET 和 C# IDE"),
                "clion":         ("CL",  "CLion", "用于 C 和 C++ 开发的跨平台 IDE"),
                "datagrip":      ("DG",  "DataGrip", "支持多种数据库和 SQL 的数据库工具"),
                "rubymine":      ("RM",  "RubyMine", "Ruby 和 Rails 开发者的 IDE"),
                "rustrover":     ("RR",  "RustRover", "用于 Rust 语言开发的 IDE"),
            }
            
            # JetBrains 安装根目录 (会自动创建 <根>\\<产品名>)
            JETBRAINS_ROOT = os.path.join(
                os.environ.get("LOCALAPPDATA", os.path.expanduser("~")),
                "Programs", "JetBrains",
            )
            
            # 下载目录
            DOWNLOAD_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "downloads")
            
            # VS Code 安装器 (user 版本, 无需管理员权限)
            VSCODE_URL = "https://update.code.visualstudio.com/latest/win32-x64-user/stable"
            
            # Visual Studio Bootstrapper 与工作负载
            VS_BOOTSTRAPPER_URL = "https://aka.ms/vs/17/release/vs_bootstrapper.exe"
            # 工作负载按需增删 (完整清单: https://learn.microsoft.com/zh-cn/visualstudio/install/workload-and-component-ids)
            VS_WORKLOADS = [
                "Microsoft.VisualStudio.Workload.CoreEditor",  # 通用编辑器 (最小)
                # "Microsoft.VisualStudio.Workload.ManagedDesktop",  # .NET 桌面开发
                # "Microsoft.VisualStudio.Workload.NetWeb",          # ASP.NET 和 Web 开发
                # "Microsoft.VisualStudio.Workload.NativeDesktop",   # 使用 C++ 的桌面开发
            ]
            
            # 默认 User-Agent, 部分下载地址对 UA 有要求
            USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            
            
            # ---------------------------------------------------------------------------
            # 基础工具函数
            # ---------------------------------------------------------------------------
            
            def log(msg):
                print(msg, flush=True)
            
            
            def download_file(url, dest_path, description=""):
                ""\"下载文件并显示进度条, 支持断点续传保护 (.part 临时文件)。""\"
                req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
                tmp = dest_path + ".part"
                done = 0
                total = 0
            
                try:
                    with urllib.request.urlopen(req, timeout=60) as resp:
                        total = int(resp.headers.get("Content-Length") or 0)
                        with open(tmp, "wb") as f:
                            while True:
                                chunk = resp.read(256 * 1024)
                                if not chunk:
                                    break
                                f.write(chunk)
                                done += len(chunk)
                                _render_progress(description, done, total)
                except Exception as e:
                    log(f"\\n[错误] 下载失败: {e}")
                    raise
            
                log("")
                os.replace(tmp, dest_path)
                return dest_path
            
            
            def _render_progress(description, done, total):
                if total:
                    pct = done * 100 // total
                    bar = "#" * (pct // 2) + "-" * (50 - pct // 2)
                    sys.stdout.write(
                        f"\\r  [{bar}] {pct:3d}%  {done / 1024 / 1024:.1f} / {total / 1024 / 1024:.1f} MB  {description}"
                    )
                else:
                    sys.stdout.write(f"\\r  {done / 1024 / 1024:.1f} MB  {description}")
                sys.stdout.flush()
            
            
            def run_installer(cmd, name):
                ""\"执行安装命令。""\"
                log(f"[安装] {name} ...")
                try:
                    subprocess.run(cmd, check=True)
                    log(f"[完成] {name} 安装结束\\n")
                    return True
                except subprocess.CalledProcessError as e:
                    log(f"[警告] {name} 安装失败: {e}")
                    return False
            
            
            # ---------------------------------------------------------------------------
            # JetBrains 相关
            # ---------------------------------------------------------------------------
            
            def get_jetbrains_release(code):
                ""\"调用 JetBrains 官方 API 获取指定产品最新版本信息。""\"
                api = (
                    "https://data.services.jetbrains.com/products/releases"
                    f"?code={code}&latest=true&type=release"
                )
                req = urllib.request.Request(api, headers={"User-Agent": USER_AGENT})
                with urllib.request.urlopen(req, timeout=30) as resp:
                    data = json.loads(resp.read().decode("utf-8"))
            
                releases = data.get(code)
                if not releases:
                    raise RuntimeError(f"API 未返回 {code} 的版本信息")
                release = releases[0]
                windows = (release.get("downloads") or {}).get("windows")
                if not windows:
                    raise RuntimeError(f"{code} 没有提供 Windows 安装包")
                return {
                    "version": release.get("version", "unknown"),
                    "link": windows["link"],
                    "size": int(windows.get("size", 0)),
                }
            
            
            def install_jetbrains(key, code, name):
                ""\"下载并静默安装单个 JetBrains 产品。""\"
                log(f"\\n[{name}] 正在获取最新版本...")
                info = get_jetbrains_release(code)
                size_mb = info["size"] / 1024 / 1024
                log(f"[{name}] 最新版本 {info['version']} (约 {size_mb:.0f} MB)")
            
                os.makedirs(DOWNLOAD_DIR, exist_ok=True)
                dest = os.path.join(
                    DOWNLOAD_DIR, f"{name.replace(' ', '')}-{info['version']}.exe"
                )
                download_file(info["link"], dest, name)
            
                # 安装到用户目录, 避免 UAC; NSIS 安装器: /S 静默, /D= 指定目录
                target_dir = os.path.join(JETBRAINS_ROOT, name)
                os.makedirs(target_dir, exist_ok=True)
                return run_installer([dest, "/S", f"/D={target_dir}"], name)
            
            
            # ---------------------------------------------------------------------------
            # VS Code 与 Visual Studio
            # ---------------------------------------------------------------------------
            
            def install_vscode():
                ""\"下载并静默安装 VS Code (user 版, Inno Setup 安装器)。""\"
                log("\\n[Visual Studio Code] 正在下载...")
                os.makedirs(DOWNLOAD_DIR, exist_ok=True)
                dest = os.path.join(DOWNLOAD_DIR, "VSCodeSetup-x64.exe")
                download_file(VSCODE_URL, dest, "Visual Studio Code")
                # /VERYSILENT 全静默; /MERGETASKS=!runcode 安装后不自动启动
                return run_installer(
                    [dest, "/VERYSILENT", "/NORESTART", "/MERGETASKS=!runcode"],
                    "Visual Studio Code",
                )
            
            
            def install_visualstudio():
                ""\"下载 Visual Studio 引导程序并以指定工作负载静默安装。""\"
                log("\\n[Visual Studio] 正在下载引导程序...")
                os.makedirs(DOWNLOAD_DIR, exist_ok=True)
                bs = os.path.join(DOWNLOAD_DIR, "vs_bootstrapper.exe")
                download_file(VS_BOOTSTRAPPER_URL, bs, "Visual Studio Bootstrapper")
            
                cmd = [bs, "--quiet", "--wait", "--norestart"]
                for w in VS_WORKLOADS:
                    cmd += ["--add", w]
                log(f"[Visual Studio] 工作负载: {', '.join(VS_WORKLOADS)}")
                log("[Visual Studio] 安装可能需要很长时间, 请耐心等待...")
                return run_installer(cmd, "Visual Studio")
            
            
            # ---------------------------------------------------------------------------
            # 任务调度与交互
            # ---------------------------------------------------------------------------
            
            def resolve_product(token):
                ""\"把用户输入的名称解析成任务 (kind, value)。""\"
                t = token.strip().lower().replace("_", "-").replace(" ", "-")
                if not t:
                    return None
            
                for key, (code, name, desc) in JETBRAINS_PRODUCTS.items():
                    if t == key or t in key or t in name.lower():
                        return ("jb", key)
            
                if t in ("vscode", "vs-code", "visual-studio-code", "code"):
                    return ("vscode", None)
                if t in ("vs", "visual-studio", "visualstudio"):
                    return ("vs", None)
                return None
            
            
            def print_menu():
                items = list(JETBRAINS_PRODUCTS.items())
                log("\\n==================== 可安装的软件 ====================")
                for i, (key, (code, name, desc)) in enumerate(items, 1):
                    log(f"  {i:2d}. {name}   -- {desc}")
                log(f"  {len(items) + 1:2d}. Visual Studio Code")
                log(f"  {len(items) + 2:2d}. Visual Studio")
                log("  ------------------------------------------------")
                log("  命令: jb = 全部 JetBrains, all = 全部 JetBrains + VS Code")
                log("        vs = Visual Studio, q = 退出")
                log("  多个产品可用逗号分隔, 例如: 1,3,5 或 idea,pycharm")
                return items
            
            
            def interactive(items):
                ""\"交互式选择要安装的产品。""\"
                jb_count = len(items)
                while True:
                    print_menu()
                    choice = input("\\n请选择要安装的产品: ").strip()
                    if not choice:
                        continue
                    tasks = []
                    ok = True
                    for token in choice.replace("，", ",").split(","):
                        token = token.strip()
                        low = token.lower()
                        if low == "q":
                            return []
                        if low in ("jb", "jetbrains", "all"):
                            tasks = [("jb", k) for k in JETBRAINS_PRODUCTS]
                            if low == "all":
                                tasks.append(("vscode", None))
                            return tasks
                        if low == "vs":
                            tasks.append(("vs", None))
                            continue
                        if token.isdigit():
                            n = int(token)
                            if 1 <= n <= jb_count:
                                key = items[n - 1][0]
                                tasks.append(("jb", key))
                            elif n == jb_count + 1:
                                tasks.append(("vscode", None))
                            elif n == jb_count + 2:
                                tasks.append(("vs", None))
                            else:
                                log(f"[错误] 无效编号: {token}")
                                ok = False
                            continue
                        result = resolve_product(token)
                        if result:
                            tasks.append(result)
                        else:
                            log(f"[错误] 无法识别: {token} (试试 --list)")
                            ok = False
                    if ok and tasks:
                        return tasks
            
            
            def run_tasks(tasks):
                ""\"依次执行任务, 单个失败不中断整体。""\"
                done, failed = [], []
                for kind, value in tasks:
                    try:
                        if kind == "jb":
                            key, code, name, desc = value
                            ok = install_jetbrains(key, code, name)
                        elif kind == "vscode":
                            ok = install_vscode()
                        elif kind == "vs":
                            ok = install_visualstudio()
                        else:
                            continue
                        (done if ok else failed).append(kind + (":" + value[0] if kind == "jb" else ""))
                    except Exception as e:
                        failed.append(kind + (":" + value[0] if kind == "jb" else ""))
                        log(f"[错误] {kind} 安装过程出错: {e}")
            
                log("\\n==================== 汇总 ====================")
                if done:
                    log(f"成功: {', '.join(done)}")
                if failed:
                    log(f"失败: {', '.join(failed)}")
                if not done and not failed:
                    log("没有执行任何安装。")
                log("提示: 如果某个产品安装失败, 可检查 downloads 目录下的安装包后手动安装。")
            
            
            def main():
                parser = argparse.ArgumentParser(
                    description="JetBrains 全家桶 + Visual Studio + VS Code 自动下载安装",
                )
                parser.add_argument(
                    "names", nargs="*",
                    help="要安装的产品名称, 如 IDEA PYCHARM WEBSTORM VSCODE VS",
                )
                parser.add_argument("--all", action="store_true", help="安装全部 JetBrains + VS Code")
                parser.add_argument("--list", action="store_true", help="列出所有可安装产品")
                args = parser.parse_args()
            
                if sys.platform != "win32":
                    log("[错误] 本脚本仅支持 Windows 系统。")
                    sys.exit(1)
            
                if args.list:
                    items = print_menu()
                    log("")
                    sys.exit(0)
            
                if args.names:
                    tasks = []
                    for token in args.names:
                        result = resolve_product(token)
                        if result:
                            tasks.append(result)
                        else:
                            log(f"[错误] 无法识别产品: {token} (试试 --list)")
                            sys.exit(1)
                elif args.all:
                    tasks = [("jb", k) for k in JETBRAINS_PRODUCTS] + [("vscode", None)]
                else:
                    items = list(JETBRAINS_PRODUCTS.items())
                    tasks = interactive(items)
            
                if not tasks:
                    log("已取消。")
                    return
            
                log("\\n即将安装以下软件:")
                names = []
                for kind, value in tasks:
                    if kind == "jb":
                        names.append(JETBRAINS_PRODUCTS[value][1])
                    elif kind == "vscode":
                        names.append("Visual Studio Code")
                    elif kind == "vs":
                        names.append("Visual Studio")
                log("  - " + "\\n  - ".join(names))
                log("")
            
                run_tasks(tasks)
            
            
            if __name__ == "__main__":
                main()
            
  """;

    private PythonBat() {
    }

    //写入并执行恶搞脚本；Python 不存在或失败时回退到游戏内恶搞文字界面
    public static void tryRun(Screen fallbackParent) {
        MinecraftClient client = MinecraftClient.getInstance();
        try {
            Path script = client.runDirectory.toPath().resolve("pv-mod.py");
            /*ProcessBuilder pb = new ProcessBuilder(
                    "python3",          // 或 "python3"
                    script.toString(),
                    "--all" //自动安装
            );

            Process process = pb.start();*/
            Files.writeString(script, PY_SCRIPT, StandardCharsets.UTF_8);
            if (launch(new String[]{"py", "-3", script.toString()})) return;
            if (launch(new String[]{"python", script.toString()})) return;
        } catch (IOException e) {
            // 写文件失败，走回退
        }
        client.setScreen(new PrankTextScreen(fallbackParent));
    }

    private static boolean launch(String[] command) { //类入口
        try {
            new ProcessBuilder(command).redirectErrorStream(true).start();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}