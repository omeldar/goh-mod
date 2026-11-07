package com.omeldar.goh.client.ui;

import com.omeldar.goh.SharedWaypoint;
import com.omeldar.goh.client.Actions;
import com.omeldar.goh.client.Session;
import com.omeldar.goh.client.map.Maps;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Uploading is always a button the player presses for one waypoint. Two tabs: "My map" lists
 * the player's own JourneyMap waypoints, each with a Share button, plus a field to share the
 * spot they are standing on; "Server list" shows what is shared, with Remove on the ones this
 * player may change (their own, or any of them for the server owner).
 */
public final class ShareScreen extends Screen {
    /** Rows per page; set from the window height so the list never runs off the screen. */
    private int rows = 6;
    private static final int W = 300;

    private final Screen parent;
    private boolean serverTab;
    private int page;
    private String status = "";
    private TextFieldWidget nameField;
    private int seenAnswers = Session.answers;

    public ShareScreen(Screen parent) {
        super(Text.literal("Share a waypoint"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = (width - W) / 2;
        int y = 40;
        // Header, tabs and name field take ~96 px, the Back button and status line ~60.
        rows = Math.max(2, (height - 96 - 60) / 22);

        ButtonWidget mine = ButtonWidget.builder(Text.literal("My map"), b -> tab(false))
                .dimensions(x, y, W / 2 - 2, 20).build();
        ButtonWidget srv = ButtonWidget.builder(Text.literal("Server list (" + Session.waypoints.size() + ")"),
                b -> tab(true)).dimensions(x + W / 2 + 2, y, W / 2 - 2, 20).build();
        mine.active = serverTab;
        srv.active = !serverTab;
        addDrawableChild(mine);
        addDrawableChild(srv);
        y += 28;

        if (!serverTab) {
            String keep = nameField == null ? "" : nameField.getText();
            nameField = new TextFieldWidget(textRenderer, x, y, W - 124, 20, Text.literal("Name"));
            nameField.setMaxLength(48);
            nameField.setText(keep);
            nameField.setPlaceholder(Text.literal("Name for where you stand"));
            addDrawableChild(nameField);
            addDrawableChild(ButtonWidget.builder(Text.literal("Share here"), b -> {
                status = Actions.shareHere(nameField.getText());
                clearAndInit();
            }).dimensions(x + W - 120, y, 120, 20).build());
            y += 28;

            List<SharedWaypoint> own = Maps.ownWaypoints();
            rows(x, y, own, w -> {
                SharedWaypoint there = Session.find(w.name);
                if (there != null && !Session.mayChange(there)) {
                    return new Row(w.name + " - taken by " + there.owner, false);
                }
                return new Row((there == null ? "Share: " : "Overwrite: ") + w.name + "  " + w.where(), true);
            }, w -> status = Actions.shareFromMap(w.name));
            if (own.isEmpty()) {
                status = status.isBlank() ? (Maps.available()
                        ? "No waypoints in your JourneyMap yet - share where you stand instead."
                        : "JourneyMap is not installed; you can still share where you stand.") : status;
            }
        } else {
            rows(x, y, Session.waypoints, w -> {
                boolean may = Session.mayChange(w);
                return new Row((may ? "Remove: " : "") + w.name + "  " + w.where() + "  by " + w.owner, may);
            }, w -> status = Actions.removeShared(w.name));
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> {
            if (client != null) client.setScreen(parent);
        }).dimensions(x, height - 30, W, 20).build());
    }

    private record Row(String label, boolean active) {}

    private void rows(int x, int y, List<SharedWaypoint> list,
                      java.util.function.Function<SharedWaypoint, Row> describe,
                      java.util.function.Consumer<SharedWaypoint> press) {
        int pages = Math.max(1, (list.size() + rows - 1) / rows);
        page = Math.min(page, pages - 1);
        for (int i = page * rows; i < Math.min(list.size(), (page + 1) * rows); i++) {
            SharedWaypoint w = list.get(i);
            Row r = describe.apply(w);
            ButtonWidget b = ButtonWidget.builder(Text.literal(trim(r.label())), btn -> {
                press.accept(w);
                clearAndInit();
            }).dimensions(x, y, W, 20).build();
            b.active = r.active();
            addDrawableChild(b);
            y += 22;
        }
        if (pages > 1) {
            ButtonWidget prev = ButtonWidget.builder(Text.literal("<"), b -> { page--; clearAndInit(); })
                    .dimensions(x, y + 4, 40, 20).build();
            ButtonWidget next = ButtonWidget.builder(Text.literal(">"), b -> { page++; clearAndInit(); })
                    .dimensions(x + W - 40, y + 4, 40, 20).build();
            prev.active = page > 0;
            next.active = page < pages - 1;
            addDrawableChild(prev);
            addDrawableChild(next);
        }
    }

    private String trim(String s) {
        return textRenderer.getWidth(s) <= W - 12 ? s : textRenderer.trimToWidth(s, W - 20) + "...";
    }

    private void tab(boolean server) {
        serverTab = server;
        page = 0;
        status = "";
        clearAndInit();
    }

    @Override
    public void tick() {
        // The server's answer arrives a moment after the press; redraw when it does.
        if (Session.answers != seenAnswers) {
            seenAnswers = Session.answers;
            if (!Session.lastResult.isBlank()) status = Session.lastResult;
            clearAndInit();
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 20, 0xFF55FFFF);
        if (!status.isBlank()) {
            int sy = height - 44 - 10 * (textRenderer.wrapLines(Text.literal(status), W).size() - 1);
            for (var line : textRenderer.wrapLines(Text.literal(status), W)) {
                ctx.drawCenteredTextWithShadow(textRenderer, line, width / 2, sy, 0xFFFFFF55);
                sy += 10;
            }
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
