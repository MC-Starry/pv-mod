package com.starry.client.mixin;

import com.starry.client.PlayVanillaState;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.world.Difficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldCreator.class)
public class WorldCreatorMixin {

    // 锁定游戏模式：选了被香草玩就永远停在极限，点别的模式也改不回去
    @Inject(method = "setGameMode", at = @At("HEAD"), cancellable = true)
    private void pv$lockGameMode(WorldCreator.Mode mode, CallbackInfo ci) {
        if (PlayVanillaState.forceHardcore && mode != WorldCreator.Mode.HARDCORE) {
            ci.cancel();
        }
    }

    // 锁定难度：只能是困难
    @Inject(method = "setDifficulty", at = @At("HEAD"), cancellable = true)
    private void pv$lockDifficulty(Difficulty difficulty, CallbackInfo ci) {
        if (PlayVanillaState.forceHardcore && difficulty != Difficulty.HARD) {
            ci.cancel();
        }
    }

    // 锁定作弊：永远为关
    @Inject(method = "setCheatsEnabled", at = @At("HEAD"), cancellable = true)
    private void pv$lockCheats(boolean cheats, CallbackInfo ci) {
        if (PlayVanillaState.forceHardcore && cheats) {
            ci.cancel();
        }
    }
}