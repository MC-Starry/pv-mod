package com.starry.client.screen;

import com.starry.client.prank.InstallerBat;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class PrankTextScreen extends Screen {
    private final Screen parent;

    public PrankTextScreen(Screen parent) {
        super(Text.literal("想被香草玩吗？"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.addDrawableChild(ButtonWidget.builder(Text.literal("好吧"), button -> this.close())
                .dimensions(this.width / 2 - 70, this.height / 2 + 30, 140, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        String[] lines = InstallerBat.XC_TEXT.split("\n");
        for (int i = 0; i < lines.length; i++) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(lines[i]),
                    this.width / 2, this.height / 2 - 30 + i * 12, 0xFFFFFF);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}