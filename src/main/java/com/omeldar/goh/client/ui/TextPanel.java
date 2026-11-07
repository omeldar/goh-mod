package com.omeldar.goh.client.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapped, centred paragraphs in a fixed box that scrolls with the mouse wheel when they do
 * not fit. Screens give it whatever height is left after their buttons, so nothing is ever
 * pushed off the bottom at small window sizes or large GUI scales.
 */
final class TextPanel {
    private static final int LINE = 10;

    private final List<OrderedText> lines = new ArrayList<>();
    private int top;
    private int bottom;
    private int scroll;

    void layout(TextRenderer font, List<String> paragraphs, int width, int top, int bottom) {
        lines.clear();
        for (String p : paragraphs) {
            if (!lines.isEmpty()) lines.add(OrderedText.EMPTY);
            lines.addAll(font.wrapLines(Text.literal(p), width));
        }
        this.top = top;
        this.bottom = Math.max(top + LINE, bottom);
        scroll = Math.min(scroll, maxScroll());
    }

    /** Height the text wants, for screens that size themselves around it. */
    int wantedHeight() {
        return lines.size() * LINE;
    }

    private int maxScroll() {
        return Math.max(0, lines.size() * LINE - (bottom - top));
    }

    boolean scroll(double amount) {
        if (maxScroll() == 0) return false;
        scroll = (int) Math.max(0, Math.min(maxScroll(), scroll - amount * LINE * 2));
        return true;
    }

    void render(DrawContext ctx, TextRenderer font, int centreX, int screenWidth) {
        ctx.enableScissor(0, top, screenWidth, bottom);
        int y = top - scroll;
        for (OrderedText line : lines) {
            if (y + LINE > top && y < bottom) {
                ctx.drawCenteredTextWithShadow(font, line, centreX, y, 0xFFDDDDDD);
            }
            y += LINE;
        }
        ctx.disableScissor();
        if (maxScroll() > 0) {
            // A thin bar on the right edge of the text, so it is obvious there is more.
            int h = bottom - top;
            int bar = Math.max(8, h * h / (lines.size() * LINE));
            int barY = top + (int) ((long) (h - bar) * scroll / maxScroll());
            int x = centreX + 170;
            ctx.fill(x, top, x + 2, bottom, 0x40FFFFFF);
            ctx.fill(x, barY, x + 2, barY + bar, 0xC0FFFFFF);
        }
    }
}
