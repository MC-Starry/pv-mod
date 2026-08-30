package com.starry.client.screen;

import com.starry.client.PlayVanillaState;
import com.starry.client.prank.PythonBat;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class ChoiceScreen extends Screen {
    private final Screen parent; //注册

    public ChoiceScreen(Screen parent) {
        super(Text.literal("玩香草还是被香草玩？"));//选择文本
        this.parent = parent;
    }

    @Override
    protected void init() {
        int buttonWidth = 140;
        int y = this.height / 2;//选择框大小

        this.addDrawableChild(ButtonWidget.builder(Text.literal("玩香草"), button -> {       //lam表达式玩香草分支
            PlayVanillaState.choiceMade = true; //这里更新选择状态
            PlayVanillaState.forceHardcore = true;//这里关闭极限锁定
            PythonBat.tryRun(this.parent);
        }).dimensions(this.width / 2 - buttonWidth - 10, y, buttonWidth, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("被香草玩"), button -> {   //被香草玩
            PlayVanillaState.choiceMade = true;  //这里更新选择状态
            PlayVanillaState.forceHardcore = true; //这里开启极限锁定
            this.close();
        }).dimensions(this.width / 2 + 10, y, buttonWidth, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title,
                this.width / 2, this.height / 2 - 30, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);   //提示框设置
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);  //主类入口
    }
}