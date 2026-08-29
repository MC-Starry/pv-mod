package com.starry.client;

import com.starry.client.screen.ChoiceScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screen.TitleScreen;

public class PlayVanillaClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // 每次启动进标题屏后弹一次二选一（choiceMade 只在本次会话内存里）
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!PlayVanillaState.choiceMade && client.currentScreen instanceof TitleScreen) {
                PlayVanillaState.choiceMade = true;
                client.setScreen(new ChoiceScreen(client.currentScreen));
            }
        });
    }
}