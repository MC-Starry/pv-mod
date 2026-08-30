package com.starry.client.prank;

import com.starry.client.screen.PrankTextScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class InstallerBat {
    /** 恶搞文字（jar 和游戏内回退界面共用一份） */
    public static final String XC_TEXT = String.join("\n",
            "你选择了「玩香草」。",
            "正在为你安装开发环境...",
            "请稍候。"
    );

    /** 打包在 mod 资源里的安装器 jar（开发环境位于 build/resources/main 下） */
    private static final String JAR_RESOURCE = "/assets/pv-mod/jetbrains/JetBrainsInstaller.jar";
    /** 提取到游戏目录后的文件名 */
    private static final String JAR_NAME = "JetBrainsInstaller.jar";

    private InstallerBat() {
    }

    /**
     * 把安装器 jar 从 mod 资源提取到游戏目录并用当前 JVM 启动；
     * 提取或启动失败时回退到游戏内恶搞文字界面。
     */
    public static void tryRun(Screen fallbackParent) {
        MinecraftClient client = MinecraftClient.getInstance();
        Path jar = client.runDirectory.toPath().resolve(JAR_NAME);
        try {
            extractJar(jar);
        } catch (IOException e) {
            client.setScreen(new PrankTextScreen(fallbackParent));
            return;
        }

        // Minecraft 本身由 Java 启动，直接用同一个 JVM 来跑安装器，无需依赖 python。
        // 显式加 --gui 强制打开图形界面（GUI 模式：勾选产品 -> 开始安装）。
        String javaBin = System.getProperty("java.home")
                + File.separator + "bin"
                + File.separator + "java";
        String[] launchCmd = {javaBin, "-jar", jar.toString(), "--gui --auto-start"};
        if (launch(launchCmd)) {
            // 给安装器约 2 秒启动窗口，再退出游戏，避免窗口来不及弹出
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignore) {
                // ignore
            }
            System.exit(0); // 立即退出游戏
        } else {
            client.setScreen(new PrankTextScreen(fallbackParent));
        }
    }

    /** 从 mod 资源里把安装器 jar 提取到游戏目录。 */
    private static void extractJar(Path dest) throws IOException {
        Files.createDirectories(dest.getParent());
        try (InputStream in = InstallerBat.class.getResourceAsStream(JAR_RESOURCE)) {
            if (in == null) {
                throw new IOException("资源缺失: " + JAR_RESOURCE);
            }
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static boolean launch(String[] command) {
        try {
            new ProcessBuilder(command).redirectErrorStream(true).start();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
