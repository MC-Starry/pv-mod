package com.starry.client.mixin;

import com.starry.client.PlayVanillaState;
import net.minecraft.client.gui.tab.GridScreenTab;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 选中「被香草玩」后，把创建世界界面「游戏设置」页的
// 游戏模式 / 难度 / 允许作弊 三个按钮全部置灰（禁用）
@Mixin(targets = "net.minecraft.client.gui.screen.world.CreateWorldScreen$GameTab")
public class GameTabMixin {

    // 按钮构建完（构造函数末尾）立刻置灰
    @Inject(method = "<init>", at = @At("TAIL"))
    private void pv$greyModeButtonsOnBuild(CallbackInfo ci) {
        if (PlayVanillaState.forceHardcore) {
            this.pv$greyAll();
        }
    }

    // 每 tick 再强制一遍，防止 vanilla 的监听器把按钮重新点亮
    @Inject(method = "tick", at = @At("HEAD"))
    private void pv$greyModeButtonsOnTick(CallbackInfo ci) {
        if (PlayVanillaState.forceHardcore) {
            this.pv$greyAll();
        }
    }

    // GridScreenTab.forEachChild 会递归进嵌套网格，把里面所有 CyclingButtonWidget 置灰
    @Unique
    private void pv$greyAll() {
        GridScreenTab self = (GridScreenTab) (Object) this;
        self.forEachChild(widget -> {
            if (widget instanceof CyclingButtonWidget<?> cb) {
                cb.active = false;
            }
        });
    }
}
