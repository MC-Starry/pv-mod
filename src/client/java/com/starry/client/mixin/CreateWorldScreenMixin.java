package com.starry.client.mixin;

import com.starry.client.PlayVanillaState;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreateWorldScreen.class)
public class CreateWorldScreenMixin {

    // 进入创建世界界面（GameTab/WorldTab 构建之前），先把状态设为 极限 + 困难 + 关作弊，让按钮直接显示正确
    @Inject(method = "init", at = @At("HEAD"))
    private void pv$forceHardcoreUi(CallbackInfo ci) {
        if (!PlayVanillaState.forceHardcore) return;
        CreateWorldScreen self = (CreateWorldScreen) (Object) this;
        WorldCreator creator = self.getWorldCreator();
        creator.setGameMode(WorldCreator.Mode.HARDCORE);
        creator.setDifficulty(Difficulty.HARD);
        creator.setCheatsEnabled(false);
    }

    // 创建世界那一刻（createLevelInfo 的普通分支，ordinal=1），把 LevelInfo 钉死成 极限/生存/困难/关作弊
    @ModifyArg(method = "createLevelInfo", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelInfo;<init>(Ljava/lang/String;Lnet/minecraft/world/GameMode;ZLnet/minecraft/world/Difficulty;ZLnet/minecraft/world/GameRules;Lnet/minecraft/resource/DataConfiguration;)V", ordinal = 1), index = 2)
    private boolean pv$forceHardcoreArg(boolean hardcore) {
        return PlayVanillaState.forceHardcore ? true : hardcore;
    }

    @ModifyArg(method = "createLevelInfo", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelInfo;<init>(Ljava/lang/String;Lnet/minecraft/world/GameMode;ZLnet/minecraft/world/Difficulty;ZLnet/minecraft/world/GameRules;Lnet/minecraft/resource/DataConfiguration;)V", ordinal = 1), index = 1)
    private GameMode pv$forceGameMode(GameMode gameMode) {
        return PlayVanillaState.forceHardcore ? GameMode.SURVIVAL : gameMode;
    }

    @ModifyArg(method = "createLevelInfo", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelInfo;<init>(Ljava/lang/String;Lnet/minecraft/world/GameMode;ZLnet/minecraft/world/Difficulty;ZLnet/minecraft/world/GameRules;Lnet/minecraft/resource/DataConfiguration;)V", ordinal = 1), index = 3)
    private Difficulty pv$forceDifficulty(Difficulty difficulty) {
        return PlayVanillaState.forceHardcore ? Difficulty.HARD : difficulty;
    }

    @ModifyArg(method = "createLevelInfo", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelInfo;<init>(Ljava/lang/String;Lnet/minecraft/world/GameMode;ZLnet/minecraft/world/Difficulty;ZLnet/minecraft/world/GameRules;Lnet/minecraft/resource/DataConfiguration;)V", ordinal = 1), index = 4)
    private boolean pv$forceNoCheats(boolean allowCommands) {
        return PlayVanillaState.forceHardcore ? false : allowCommands;
    }
}
