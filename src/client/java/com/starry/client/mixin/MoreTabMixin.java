package com.starry.client.mixin;

import com.starry.client.PlayVanillaState;
import net.minecraft.client.gui.tab.GridScreenTab;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 选中「被香草玩」后，把创建世界界面「更多」页的
// 游戏规则 / 实验 / 数据包 三个按钮全部置灰（禁用）
@Mixin(targets = "net.minecraft.client.gui.screen.world.CreateWorldScreen$MoreTab")
public class MoreTabMixin {

    // 按钮构建完（构造函数末尾）立刻置灰
    @Inject(method = "<init>", at = @At("TAIL"))
    private void pv$greyMoreButtonsOnBuild(CallbackInfo ci) {
        if (!PlayVanillaState.forceHardcore) return;
        GridScreenTab self = (GridScreenTab) (Object) this;
        self.forEachChild(widget -> {
            if (widget instanceof ButtonWidget button) {
                button.active = false;
            }
        });
    }
}