package com.starry.client.mixin;

import com.starry.client.PlayVanillaState;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.world.Difficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreateWorldScreen.class)
public class CreateWorldScreenMixin {

    // 每次进入创建世界界面，先把底层状态设为 极限 + 困难 + 关作弊
    @Inject(method = "init", at = @At("TAIL"))
    private void pv$forceHardcoreUi(CallbackInfo ci) {
        if (!PlayVanillaState.forceHardcore) return;
        CreateWorldScreen self = (CreateWorldScreen) (Object) this;
        WorldCreator creator = self.getWorldCreator();
        creator.setGameMode(WorldCreator.Mode.HARDCORE);
        creator.setDifficulty(Difficulty.HARD);
        creator.setCheatsEnabled(false);
    }
}