package com.starry.client.mixin;

import com.starry.client.PlayVanillaState;
import net.minecraft.client.gui.screen.OpenToLanScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OpenToLanScreen.class)
public class OpenToLanScreenMixin {

    @Shadow
    private boolean allowCommands;

    // 进入开局域网界面：作弊直接为关
    @Inject(method = "init", at = @At("TAIL"))
    private void pv$lanCheatsOffInit(CallbackInfo ci) {
        if (PlayVanillaState.forceHardcore) this.allowCommands = false;
    }

    // 真正执行 openToLan 之前：强制作弊为关（method_19851 = 开 LAN 的方法）
    @Inject(method = "method_19851", at = @At("HEAD"))
    private void pv$lanCheatsOffOnOpen(CallbackInfo ci) {
        if (PlayVanillaState.forceHardcore) this.allowCommands = false;
    }

    // 点「允许作弊」循环按钮的回调：让点它无效
    @Inject(method = "method_32639", at = @At("HEAD"))
    private void pv$lanCheatsOffOnToggle(CallbackInfo ci) {
        if (PlayVanillaState.forceHardcore) this.allowCommands = false;
    }
}