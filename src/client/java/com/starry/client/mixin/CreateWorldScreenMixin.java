package com.starry.client.mixin;

import com.starry.client.PlayVanillaState;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.world.Difficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreateWorldScreen.class)
public class CreateWorldScreenMixin {

    // 进入创建世界界面时，把底层状态设成硬核 + 困难 + 关作弊
    @Inject(method = "init", at = @At("TAIL"))
    private void pv$forceHardcoreUi(CallbackInfo ci) {
        if (!PlayVanillaState.forceHardcore) return;
        CreateWorldScreen self = (CreateWorldScreen) (Object) this;
        WorldCreator creator = self.getWorldCreator();
        creator.setGameMode(WorldCreator.Mode.HARDCORE);
        creator.setDifficulty(Difficulty.HARD);
        creator.setCheatsEnabled(false);
    }

    // 兜底：创建世界那一刻，无论 UI 上怎么点，硬核参数强制为 true
    @ModifyVariable(method = "createLevelInfo", at = @At("HEAD"), ordinal = 0, argsOnly = true, index = 1)
    private boolean pv$forceHardcoreLevel(boolean hardcore) {
        return PlayVanillaState.forceHardcore ? true : hardcore;
    }
}