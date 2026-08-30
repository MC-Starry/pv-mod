#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
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
  python install_ides.py                 # 打开图形界面 (GUI)
  python install_ides.py --gui           # 同上
  python install_ides.py --gui --auto-start    # GUI 打开后自动全选 JetBrains + VS Code 并自动开始安装
  python install_ides.py --cli --all     # 命令行安装全部 JetBrains + VS Code
  python install_ides.py --cli --all --auto   # 命令行全自动静默安装
  python install_ides.py --list          # 列出所有产品
  python install_ides.py IDEA PYCHARM    # 命令行按名称安装

说明:
  - 仅支持 Windows。
  - JetBrains 最新版本信息通过官方 API 获取:
    https://data.services.jetbrains.com/products/releases?code=XX&latest=true&type=release
  - 默认下载目录: 脚本所在目录下的 downloads 文件夹。
  - JetBrains 默认安装到 %LOCALAPPDATA%\\Programs\\JetBrains\\<产品名>
    (安装向导已预填该目录, 避免 UAC 权限问题); 如需装到 Program Files 请修改 JETBRAINS_ROOT。
  - 安装方式:
      默认    JetBrains / VS Code 弹出完整安装向导, Visual Studio 用 --passive 显示进度。
      --auto  全自动静默安装 (JetBrains / VS Code 不弹向导), Visual Studio 仍显示进度。
              注意: JetBrains 的 NSIS 安装器没有"半自动"模式, 全自动即静默,
              但脚本自身的界面会实时显示每个产品的下载与安装进度。
  - Visual Studio 体积很大, 默认只安装"通用编辑器"工作负载,
    可按需在 VS_WORKLOADS 中增删工作负载。
"""

import argparse
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

# 统一标准输出编码为 UTF-8, 避免中文在 Windows 控制台乱码 (Python 3.7+)
for _stream in (sys.stdout, sys.stderr):
  try:
      _stream.reconfigure(encoding="utf-8")
  except Exception:
      pass

# tkinter 仅在需要 GUI 时才会真正使用; 没有 GUI 环境也能用命令行模式
try:
  import queue
  import threading
  import time
  import tkinter as tk
  from tkinter import ttk
  HAVE_TK = True
except Exception:
  HAVE_TK = False

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

# JetBrains 安装根目录 (会自动创建 <根>\<产品名>)
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

# 网络请求最大重试次数 (国内 JetBrains CDN 间歇性返回 404, 多试几次更稳)
DOWNLOAD_RETRIES = 6

# ---------------------------------------------------------------------------
# UI 桥接: 有 GUI 时指向 InstallerGUI 实例, 命令行模式为 None
# ---------------------------------------------------------------------------
UI = None

# 全自动模式: True 时不弹出安装向导, 静默自动完成 (Visual Studio 始终显示进度)
AUTO_MODE = False


# ---------------------------------------------------------------------------
# 基础工具函数
# ---------------------------------------------------------------------------

def log(msg):
  """输出日志: 有 GUI 时同时送入窗口日志区, 否则打印到控制台。"""
  if UI is not None:
      UI.log(str(msg))
  print(msg, flush=True)


# ---------------------------------------------------------------------------
# 网络层: 手动跟随重定向, 强制 Connection: keep-alive
# 国内 JetBrains CDN (download-cdn.clf.jetbrains.com.cn) 对 urllib 默认的
# Connection: close 会返回 HTTP 404, 必须显式 keep-alive 才能下载。
# ---------------------------------------------------------------------------
class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


_NO_REDIRECT_OPENER = urllib.request.build_opener(_NoRedirect)


def _open_keepalive(url, timeout):
  """打开 URL 并手动跟随重定向, 每一跳都强制 Connection: keep-alive。"""
  current = url
  for _ in range(5):
      req = urllib.request.Request(
          current,
          headers={"User-Agent": USER_AGENT, "Connection": "keep-alive"},
      )
      try:
          return _NO_REDIRECT_OPENER.open(req, timeout=timeout)
      except urllib.error.HTTPError as e:
          if e.code in (301, 302, 303, 307, 308):
              loc = e.headers.get("Location")
              e.close()
              if not loc:
                  raise
              current = (
                  loc if loc.startswith("http")
                  else urllib.parse.urljoin(current, loc)
              )
          else:
              raise
  raise RuntimeError("重定向次数过多, 请检查下载地址")


def _download_with_curl(url, dest_path, description=""):
  """用系统自带 curl 下载 (绕开 urllib 与国内 CDN 的兼容问题)。

  curl 自带重试, 是本脚本的默认下载方式; 进度显示在控制台 (stderr)。
  """
  tmp = dest_path + ".part"
  cmd = [
      "curl", "-L", "--fail",
      "--retry", "5", "--retry-delay", "1", "--retry-all-errors",
      "-A", USER_AGENT,
      "-o", tmp, url,
  ]
  log(f"[下载] 使用系统 curl 兜底下载 {description} ...")
  subprocess.run(cmd, check=True)
  os.replace(tmp, dest_path)
  return dest_path


def download_file(url, dest_path, description="", progress_callback=None):
  """下载文件, 优先使用系统 curl (国内 CDN 下最稳定, 自带重试);
  curl 失败时回退 urllib 带进度条下载。

  progress_callback(done, total): 可选, 由调用方(如 GUI)实时获取下载进度,
  仅在 urllib 回退路径中生效。
  """
  tmp = dest_path + ".part"

  # 优先 curl: 绕开 urllib 与国内 JetBrains CDN 的兼容问题
  try:
      return _download_with_curl(url, dest_path, description)
  except Exception as e:
      if os.path.exists(tmp):
          try:
              os.remove(tmp)
          except OSError:
              pass
      log(f"[网络] curl 下载失败, 回退 urllib 重试: {e}")

  for attempt in range(1, DOWNLOAD_RETRIES + 1):
      if UI is not None and UI.is_cancel():
          raise RuntimeError("用户取消下载")
      done = 0
      total = 0
      try:
          with _open_keepalive(url, 60) as resp:
              total = int(resp.headers.get("Content-Length") or 0)
              with open(tmp, "wb") as f:
                  while True:
                      if UI is not None and UI.is_cancel():
                          raise RuntimeError("用户取消下载")
                      chunk = resp.read(256 * 1024)
                      if not chunk:
                          break
                      f.write(chunk)
                      done += len(chunk)
                      if progress_callback is not None:
                          progress_callback(done, total)
                      else:
                          _render_progress(description, done, total)
          os.replace(tmp, dest_path)
          if progress_callback is not None:
              progress_callback(done, total)
          else:
              log("")
          return dest_path
      except RuntimeError:
          # 用户取消, 不重试; 清理临时文件
          if os.path.exists(tmp):
              try:
                  os.remove(tmp)
              except OSError:
                  pass
          raise
      except Exception as e:
          if os.path.exists(tmp):
              try:
                  os.remove(tmp)
              except OSError:
                  pass
          # CDN 间歇性 404: 快速重试 (1s) 更容易命中可用节点;
          # 连续 3 次失败则立即切换到 curl 兜底, 避免干等
          if attempt >= 3:
              log(f"[网络] {description} 连续失败, 改用系统 curl 兜底下载...")
              return _download_with_curl(url, dest_path, description)
          if attempt < DOWNLOAD_RETRIES:
              log(f"[网络] 下载 {description} 失败 (第 {attempt}/{DOWNLOAD_RETRIES} 次): {e}")
              log(f"         1 秒后自动重试...")
              time.sleep(1)
          else:
              raise


def _render_progress(description, done, total):
  if total:
      pct = done * 100 // total
      bar = "#" * (pct // 2) + "-" * (50 - pct // 2)
      sys.stdout.write(
          f"\r  [{bar}] {pct:3d}%  {done / 1024 / 1024:.1f} / {total / 1024 / 1024:.1f} MB  {description}"
      )
  else:
      sys.stdout.write(f"\r  {done / 1024 / 1024:.1f} MB  {description}")
  sys.stdout.flush()


def run_installer(cmd, name):
  """执行安装命令。"""
  log(f"[安装] {name} ...")
  try:
      subprocess.run(cmd, check=True)
      log(f"[完成] {name} 安装结束\n")
      return True
  except subprocess.CalledProcessError as e:
      log(f"[警告] {name} 安装失败: {e}")
      return False


# ---------------------------------------------------------------------------
# JetBrains 相关
# ---------------------------------------------------------------------------

def get_jetbrains_release(code):
  """调用 JetBrains 官方 API 获取指定产品最新版本信息 (网络失败自动重试 3 次)。"""
  api = (
      "https://data.services.jetbrains.com/products/releases"
      f"?code={code}&latest=true&type=release"
  )
  last_err = None
  for attempt in range(1, DOWNLOAD_RETRIES + 1):
      try:
          with _open_keepalive(api, 30) as resp:
              data = json.loads(resp.read().decode("utf-8"))
          break
      except Exception as e:
          last_err = e
          log(f"[网络] 获取 {code} 版本信息失败 (第 {attempt}/{DOWNLOAD_RETRIES} 次): {e}")
          if attempt < DOWNLOAD_RETRIES:
              log(f"         1 秒后自动重试...")
              time.sleep(1)
  else:
      raise RuntimeError(
          "无法获取 JetBrains 版本信息, 可能是网络问题。"
          "如使用代理, 请设置环境变量 HTTPS_PROXY 后再试。"
          f"最后一次错误: {last_err}"
      )

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
  """下载并静默安装单个 JetBrains 产品。"""
  if UI is not None:
      UI.set_status(key, "获取版本...")
  log(f"\n[{name}] 正在获取最新版本...")
  info = get_jetbrains_release(code)
  size_mb = info["size"] / 1024 / 1024
  log(f"[{name}] 最新版本 {info['version']} (约 {size_mb:.0f} MB)")

  os.makedirs(DOWNLOAD_DIR, exist_ok=True)
  dest = os.path.join(
      DOWNLOAD_DIR, f"{name.replace(' ', '')}-{info['version']}.exe"
  )

  if UI is not None:
      UI.set_status(key, "下载中...")

      def _cb(done, total):
          UI.progress(key, done, total)

      download_file(info["link"], dest, name, progress_callback=_cb)
  else:
      download_file(info["link"], dest, name)

  # 安装到用户目录, 避免 UAC; NSIS 安装器:
  #   默认: 前台安装向导, /D= 预填默认目录
  #   AUTO_MODE: /S 全自动静默安装
  target_dir = os.path.join(JETBRAINS_ROOT, name)
  os.makedirs(target_dir, exist_ok=True)
  if UI is not None:
      UI.set_status(key, "安装中...")
  installer_args = [f"/D={target_dir}"]
  if AUTO_MODE:
      installer_args.insert(0, "/S")
  ok = run_installer([dest] + installer_args, name)
  if UI is not None:
      UI.set_status(key, "完成" if ok else "失败")
  return ok


# ---------------------------------------------------------------------------
# VS Code 与 Visual Studio
# ---------------------------------------------------------------------------

def install_vscode():
  """下载并静默安装 VS Code (user 版, Inno Setup 安装器)。"""
  key = "vscode"
  if UI is not None:
      UI.set_status(key, "下载中...")
  log("\n[Visual Studio Code] 正在下载...")
  os.makedirs(DOWNLOAD_DIR, exist_ok=True)
  dest = os.path.join(DOWNLOAD_DIR, "VSCodeSetup-x64.exe")

  if UI is not None:
      def _cb(done, total):
          UI.progress(key, done, total)

      download_file(VSCODE_URL, dest, "Visual Studio Code", progress_callback=_cb)
  else:
      download_file(VSCODE_URL, dest, "Visual Studio Code")

  if UI is not None:
      UI.set_status(key, "安装中...")
  # 默认: 前台安装向导; AUTO_MODE: /VERYSILENT 全静默自动安装
  # /MERGETASKS=!runcode 安装后不自动启动
  cmd = [dest, "/NORESTART", "/MERGETASKS=!runcode"]
  if AUTO_MODE:
      cmd.insert(1, "/VERYSILENT")
  ok = run_installer(cmd, "Visual Studio Code")
  if UI is not None:
      UI.set_status(key, "完成" if ok else "失败")
  return ok


def install_visualstudio():
  """下载 Visual Studio 引导程序并以指定工作负载静默安装。"""
  key = "vs"
  if UI is not None:
      UI.set_status(key, "下载中...")
  log("\n[Visual Studio] 正在下载引导程序...")
  os.makedirs(DOWNLOAD_DIR, exist_ok=True)
  bs = os.path.join(DOWNLOAD_DIR, "vs_bootstrapper.exe")

  if UI is not None:
      def _cb(done, total):
          UI.progress(key, done, total)

      download_file(VS_BOOTSTRAPPER_URL, bs, "Visual Studio Bootstrapper", progress_callback=_cb)
  else:
      download_file(VS_BOOTSTRAPPER_URL, bs, "Visual Studio Bootstrapper")

  # --passive: 显示进度窗口并全自动完成, 无需手动交互 (默认与 AUTO_MODE 均适用)
  cmd = [bs, "--passive", "--wait", "--norestart"]
  for w in VS_WORKLOADS:
      cmd += ["--add", w]
  log(f"[Visual Studio] 工作负载: {', '.join(VS_WORKLOADS)}")
  log("[Visual Studio] 安装可能需要很长时间, 请耐心等待...")
  if UI is not None:
      UI.set_status(key, "安装中...")
  ok = run_installer(cmd, "Visual Studio")
  if UI is not None:
      UI.set_status(key, "完成" if ok else "失败")
  return ok


# ---------------------------------------------------------------------------
# 任务调度 (命令行与 GUI 共用)
# ---------------------------------------------------------------------------

def resolve_product(token):
  """把用户输入的名称解析成任务 (kind, value)。"""
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
  log("\n==================== 可安装的软件 ====================")
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
  """交互式选择要安装的产品 (命令行模式)。"""
  jb_count = len(items)
  while True:
      print_menu()
      choice = input("\n请选择要安装的产品: ").strip()
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
  """依次执行任务, 单个失败不中断整体。"""
  done, failed = [], []
  for kind, value in tasks:
      if UI is not None and UI.is_cancel():
          log("用户取消, 停止后续任务。")
          break
      key = value if kind == "jb" else kind
      try:
          if kind == "jb":
              code, name, _desc = JETBRAINS_PRODUCTS[value]
              ok = install_jetbrains(value, code, name)
          elif kind == "vscode":
              ok = install_vscode()
          elif kind == "vs":
              ok = install_visualstudio()
          else:
              continue
          (done if ok else failed).append(key)
      except Exception as e:
          failed.append(key)
          log(f"[错误] {key} 安装过程出错: {e}")
          if UI is not None:
              UI.set_status(key, "失败")

  log("\n==================== 汇总 ====================")
  if done:
      log(f"成功: {', '.join(done)}")
  if failed:
      log(f"失败: {', '.join(failed)}")
  if not done and not failed:
      log("没有执行任何安装。")
  log("提示: 如果某个产品安装失败, 可检查 downloads 目录下的安装包后手动安装。")


# ---------------------------------------------------------------------------
# 图形界面 (tkinter)
# ---------------------------------------------------------------------------

if HAVE_TK:

  class InstallerGUI:
      """tkinter 图形界面: 勾选产品 -> 后台线程下载安装 -> 实时进度。"""

      def __init__(self, root, auto_start=False):
          global UI
          UI = self

          self.root = root
          self.root.title("JetBrains IDE 全家桶 + VS Code + Visual Studio 一键安装")
          self.root.geometry("760x640")
          self.root.minsize(660, 560)

          self.rows = {}          # key -> {"var":BooleanVar, "bar":Progressbar, "status":Label}
          self.msg_queue = queue.Queue()
          self.running = False
          self.cancel_flag = False
          self._last_progress_ts = 0.0

          self._build_widgets()
          self._poll()
          if auto_start:
              self.root.after(600, self._auto_start)

      # ---------- UI 搭建 ----------

      def _build_widgets(self):
          top = ttk.Label(
              self.root,
              text="勾选要安装的软件, 点击「开始安装」自动下载并安装",
              font=("Microsoft YaHei UI", 11, "bold"),
          )
          top.pack(padx=12, pady=(10, 4), anchor="w")

          # 产品列表
          list_frame = ttk.Frame(self.root)
          list_frame.pack(fill="x", padx=12)
          for key, (code, name, desc) in JETBRAINS_PRODUCTS.items():
              self._add_row(list_frame, key, f"{name}  —  {desc}", default=True)
          self._add_row(list_frame, "vscode", "Visual Studio Code", default=True)
          self._add_row(
              list_frame,
              "vs",
              "Visual Studio (体积大, 默认仅装通用编辑器)",
              default=False,
          )

          # 全自动模式勾选框 (默认勾选: 静默安装, 无需手动点向导)
          self.auto_var = tk.BooleanVar(value=True)
          ttk.Checkbutton(
              self.root,
              text="全自动模式: 静默安装, 不弹出安装向导 (Visual Studio 仍显示进度)",
              variable=self.auto_var,
          ).pack(padx=12, pady=(0, 4), anchor="w")

          # 按钮行
          btns = ttk.Frame(self.root)
          btns.pack(fill="x", padx=12, pady=6)
          ttk.Button(btns, text="全选", command=self.select_all).pack(side="left")
          ttk.Button(btns, text="反选", command=self.invert_selection).pack(side="left", padx=4)
          ttk.Button(btns, text="全部 JetBrains", command=self.select_jetbrains).pack(side="left")
          self.start_btn = ttk.Button(btns, text="开始安装", command=self.start)
          self.start_btn.pack(side="right")
          ttk.Button(btns, text="退出", command=self.root.destroy).pack(side="right", padx=4)

          # 日志区
          log_frame = ttk.Frame(self.root)
          log_frame.pack(fill="both", expand=True, padx=12, pady=(0, 10))
          self.log_text = tk.Text(
              log_frame, height=10, state="disabled", wrap="word",
              font=("Consolas", 9),
          )
          sb = ttk.Scrollbar(log_frame, command=self.log_text.yview)
          self.log_text.configure(yscrollcommand=sb.set)
          sb.pack(side="right", fill="y")
          self.log_text.pack(side="left", fill="both", expand=True)

      def _add_row(self, parent, key, label, default=True):
          frame = ttk.Frame(parent)
          frame.pack(fill="x", pady=2)
          var = tk.BooleanVar(value=default)
          ttk.Checkbutton(frame, text=label, variable=var).pack(side="left")
          status = ttk.Label(frame, text="待选", width=10, anchor="center")
          status.pack(side="right")
          bar = ttk.Progressbar(frame, maximum=100, length=200)
          bar.pack(side="right", padx=8)
          self.rows[key] = {"var": var, "bar": bar, "status": status}

      # ---------- 按钮回调 ----------

      def select_all(self):
          for r in self.rows.values():
              r["var"].set(True)

      def select_jb_and_vscode(self):
          """全选所有 JetBrains + VS Code (不含体积巨大的 Visual Studio)。"""
          for key in JETBRAINS_PRODUCTS:
              self.rows[key]["var"].set(True)
          self.rows["vscode"]["var"].set(True)
          self.rows["vs"]["var"].set(False)

      def _auto_start(self):
          """自动全选 JetBrains + VS Code 并自动开始安装 (配合 --auto-start 使用)。"""
          if self.running:
              return
          self.append_log("自动全选 JetBrains + VS Code 并开始安装...")
          self.select_jb_and_vscode()
          self.start()

      def invert_selection(self):
          for r in self.rows.values():
              r["var"].set(not r["var"].get())

      def select_jetbrains(self):
          for key in JETBRAINS_PRODUCTS:
              self.rows[key]["var"].set(True)
          self.rows["vscode"]["var"].set(False)
          self.rows["vs"]["var"].set(False)

      def start(self):
          if self.running:
              # 运行中再点一次 = 取消
              self.cancel_flag = True
              self.append_log("正在取消...")
              self.start_btn.config(text="正在取消...")
              return

          global AUTO_MODE
          AUTO_MODE = self.auto_var.get()
          self.append_log(
              "全自动模式: 开启 (静默自动完成)" if AUTO_MODE
              else "全自动模式: 关闭 (每个产品弹出安装向导)"
          )
          tasks = []
          for key in self.rows:
              if self.rows[key]["var"].get():
                  if key in JETBRAINS_PRODUCTS:
                      tasks.append(("jb", key))
                  elif key == "vscode":
                      tasks.append(("vscode", None))
                  elif key == "vs":
                      tasks.append(("vs", None))
          if not tasks:
              self.append_log("请先勾选至少一个软件。")
              return

          self.running = True
          self.cancel_flag = False
          self.start_btn.config(text="取消")
          for r in self.rows.values():
              r["status"].config(text="待选")
              r["bar"]["value"] = 0
          self.append_log("开始安装...")
          threading.Thread(target=self._worker, args=(tasks,), daemon=True).start()

      def _worker(self, tasks):
          try:
              run_tasks(tasks)
          finally:
              self.msg_queue.put(("done", None))

      # ---------- UI 桥接接口 (worker 线程调用, 通过队列转发到主线程) ----------

      def log(self, msg):
          self.msg_queue.put(("log", str(msg)))

      def progress(self, key, done, total):
          # 节流: 最多约 10 次/秒, 避免刷爆队列
          now = time.monotonic()
          if done < total and now - self._last_progress_ts < 0.1:
              return
          self._last_progress_ts = now
          self.msg_queue.put(("progress", key, done, total))

      def set_status(self, key, text):
          self.msg_queue.put(("status", key, text))

      def is_cancel(self):
          return self.cancel_flag

      # ---------- 主线程轮询队列 ----------

      def _poll(self):
          try:
              while True:
                  msg = self.msg_queue.get_nowait()
                  kind = msg[0]
                  if kind == "log":
                      self.append_log(msg[1])
                  elif kind == "progress":
                      _, key, done, total = msg
                      self._update_progress(key, done, total)
                  elif kind == "status":
                      _, key, text = msg
                      self._set_status(key, text)
                  elif kind == "done":
                      self.running = False
                      self.start_btn.config(text="开始安装")
                      self.append_log("—— 全部任务结束 ——")
          except queue.Empty:
              pass
          self.root.after(100, self._poll)

      def append_log(self, line):
          self.log_text.config(state="normal")
          self.log_text.insert("end", line + "\n")
          self.log_text.see("end")
          self.log_text.config(state="disabled")

      def _update_progress(self, key, done, total):
          row = self.rows.get(key)
          if not row:
              return
          if total:
              pct = min(100, int(done * 100 / total))
              row["bar"]["value"] = pct
              row["status"].config(text=f"{pct}%")
          else:
              row["bar"]["value"] = 0
              row["status"].config(text=f"{done / 1024 / 1024:.0f} MB")

      def _set_status(self, key, text):
          row = self.rows.get(key)
          if row:
              row["status"].config(text=text)


def launch_gui(auto_start=False):
  if not HAVE_TK:
      log("[错误] 当前环境无法加载 tkinter (图形界面不可用)。")
      log("请改用命令行模式: python install_ides.py --cli --all")
      sys.exit(1)
  root = tk.Tk()
  InstallerGUI(root, auto_start)
  root.mainloop()


# ---------------------------------------------------------------------------
# 命令行入口
# ---------------------------------------------------------------------------

def main():
  parser = argparse.ArgumentParser(
      description="JetBrains 全家桶 + Visual Studio + VS Code 自动下载安装",
  )
  parser.add_argument(
      "names", nargs="*",
      help="要安装的产品名称, 如 IDEA PYCHARM WEBSTORM VSCODE VS",
  )
  parser.add_argument("--gui", action="store_true", help="打开图形界面")
  parser.add_argument("--cli", action="store_true", help="强制使用命令行模式")
  parser.add_argument("--all", action="store_true", help="安装全部 JetBrains + VS Code")
  parser.add_argument("--list", action="store_true", help="列出所有可安装产品")
  parser.add_argument("--auto", action="store_true", help="全自动模式: 静默安装, 不弹出安装向导")
  parser.add_argument("--auto-start", action="store_true", help="GUI 打开后自动全选所有产品并自动开始安装")
  args = parser.parse_args()

  if sys.platform != "win32":
      log("[错误] 本脚本仅支持 Windows 系统。")
      sys.exit(1)

  global AUTO_MODE
  AUTO_MODE = args.auto
  cli_mode = args.cli or args.all or args.list or bool(args.names) or args.auto

  if not cli_mode or args.gui:
      launch_gui(args.auto_start)
      return

  if args.list:
      print_menu()
      log("")
      return

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

  log("\n即将安装以下软件:")
  names = []
  for kind, value in tasks:
      if kind == "jb":
          names.append(JETBRAINS_PRODUCTS[value][1])
      elif kind == "vscode":
          names.append("Visual Studio Code")
      elif kind == "vs":
          names.append("Visual Studio")
  log("  - " + "\n  - ".join(names))
  log("")

  run_tasks(tasks)


if __name__ == "__main__":
  main()





