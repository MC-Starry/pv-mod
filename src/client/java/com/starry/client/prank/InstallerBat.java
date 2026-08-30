package com.starry.client.prank;

import com.starry.client.screen.PrankTextScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class InstallerBat {
    /** 恶搞文字（jar 和游戏内回退界面共用一份） */
    public static final String XC_TEXT = String.join("\n",
            "你选择了「玩香草」。",
            "正在为你安装开发环境...",
            "请稍候。"
    );

    /** 打包在 mod 资源里的安装器 jar（开发环境位于 build/resources/main 下） */
    private static final String[] JAR_RESOURCES = {
            "assets/pv-mod/jetbrains/JetBrainsInstaller.jar",
            "/assets/pv-mod/jetbrains/JetBrainsInstaller.jar",
    };
    /** 提取到游戏目录后的文件名 */
    private static final String JAR_NAME = "JetBrainsInstaller.jar";
    /** 启动安装器时显式带上的参数：GUI 模式 + 打开后自动全选并开始安装 */
    private static final String[] INSTALLER_ARGS = {"--gui", "--auto-start"};

    private InstallerBat() {
    }

    /**
     * 把安装器 jar 从 mod 资源提取到游戏目录并用当前 JVM 启动；
     * 提取或启动失败时回退到游戏内恶搞文字界面（并把错误写入 InstallerBat_error.txt）。
     */
    public static void tryRun(Screen fallbackParent) {
        MinecraftClient client = MinecraftClient.getInstance();
        Path runDir = client.runDirectory.toPath();
        try {
            Path jar = extractJar(runDir);
            launchInstaller(jar);
            // 给安装器约 2 秒启动窗口，再退出游戏，避免窗口来不及弹出
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignore) {
                // ignore
            }
            System.exit(0);
        } catch (Throwable t) {
            reportError(runDir, t);
            client.setScreen(new PrankTextScreen(fallbackParent));
        }
    }

    /** 从 mod 资源里把安装器 jar 提取到游戏目录。 */
    private static Path extractJar(Path runDir) throws IOException {
        InputStream in = null;
        for (String res : JAR_RESOURCES) {
            // 先按原路径（可能带前导 /）从自身类加载器找
            in = InstallerBat.class.getResourceAsStream(res);
            if (in == null) {
                // 再退回线程上下文类加载器（覆盖部分加载器场景）
                String plain = res.startsWith("/") ? res.substring(1) : res;
                ClassLoader ctx = Thread.currentThread().getContextClassLoader();
                in = ctx != null ? ctx.getResourceAsStream(plain) : null;
            }
            if (in != null) {
                break;
            }
        }
        if (in == null) {
            throw new IOException("mod 资源里找不到安装器 jar: assets/pv-mod/jetbrains/JetBrainsInstaller.jar");
        }
        byte[] data;
        try (InputStream stream = in) {
            data = stream.readAllBytes();
        }
        Files.createDirectories(runDir);
        Path dest = runDir.resolve(JAR_NAME);
        try {
            Files.write(dest, data);
        } catch (FileSystemException e) {
            // 目标文件被上次残留的安装器进程占用（Windows 会锁住正在运行的 jar），
            // 改用带时间戳的新文件名，避免覆盖冲突
            dest = runDir.resolve("JetBrainsInstaller-" + System.currentTimeMillis() + ".jar");
            Files.write(dest, data);
        }
        return dest;
    }

    private static void launchInstaller(Path jar) throws IOException {
        // 优先用当前 JVM（Minecraft 本身由 Java 启动），失败则退回 PATH 上的 java
        String[] javaCandidates = {
                System.getProperty("java.home") + File.separator + "bin" + File.separator + "java",
                "java",
        };
        IOException last = null;
        for (String javaBin : javaCandidates) {
            try {
                String[] cmd = new String[3 + INSTALLER_ARGS.length];
                cmd[0] = javaBin;
                cmd[1] = "-jar";
                cmd[2] = jar.toString();
                System.arraycopy(INSTALLER_ARGS, 0, cmd, 3, INSTALLER_ARGS.length);
                new ProcessBuilder(cmd).redirectErrorStream(true).start();
                return;
            } catch (IOException e) {
                last = e;
            }
        }
        throw last != null ? last : new IOException("无法启动 java 进程");
    }

    /** 把错误写进游戏日志和 run 目录下的错误文件，便于排查。 */
    private static void reportError(Path runDir, Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        String text = "[InstallerBat] 启动安装器失败:\n" + sw;
        System.err.println(text);
        try {
            Files.writeString(runDir.resolve("InstallerBat_error.txt"), text, StandardCharsets.UTF_8);
        } catch (IOException ignore) {
            // ignore
        }
    }
}
