package com.omeldar.goh.client.ui;

import com.omeldar.goh.client.Actions;
import com.omeldar.goh.client.GohClient;
import com.omeldar.goh.client.TerrainProgress;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * The one question before distant terrain starts: here is roughly how much and how long, you
 * can play meanwhile, and you can stop at any point. Nothing loads until "Start".
 */
final class TerrainConfirmScreen extends Screen implements GohScreen {
    private static final int BUTTON_WIDTH = 220;

    private final Screen parent;
    private final TextPanel text = new TextPanel();

    TerrainConfirmScreen(Screen parent) {
        super(Text.literal("Load distant terrain?"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = (width - BUTTON_WIDTH) / 2;
        int y = height - 8 - 2 * 22;
        List<String> p = new ArrayList<>();
        String estimate = TerrainProgress.estimate();
        p.add(estimate == null
                ? "The server has not said how much terrain there is yet, so there is no estimate."
                : "This server has " + estimate + ".");
        p.add("You can keep playing while it loads. Progress shows in chat every half minute.");
        p.add("Stop at any point with the [Stop] link in chat, /goh terrain off, or this screen. "
            + "What has loaded stays, and turning it back on carries on from there.");
        text.layout(textRenderer, p, Math.min(320, width - 40), 30, y - 8);

        addDrawableChild(ButtonWidget.builder(Text.literal("Start loading"), b -> {
            close();
            GohClient.say(Actions.terrain(true), true);
        }).dimensions(x, y, BUTTON_WIDTH, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Not now"), b -> {
            if (client != null) client.setScreen(parent);
        }).dimensions(x, y + 22, BUTTON_WIDTH, 20).build());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        return text.scroll(vertical) || super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title.copy().formatted(Formatting.BOLD),
                width / 2, 12, 0xFF55FFFF);
        text.render(ctx, textRenderer, width / 2, width);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
