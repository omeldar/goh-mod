package com.omeldar.goh.client.ui;

import com.omeldar.goh.client.Actions;
import com.omeldar.goh.client.ClientConfig;
import com.omeldar.goh.client.GohClient;
import com.omeldar.goh.client.Session;
import com.omeldar.goh.client.Terrain;
import com.omeldar.goh.client.map.Maps;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * The welcome screen: what this server is, and what the player can switch on. A second page
 * introduces Lumi.
 *
 * <p>Laid out from the bottom up so it fits at the default window size and GUI scale: the
 * buttons are placed first, and the text gets whatever height is left, scrolling if it needs
 * to. The explanations live in button tooltips rather than in paragraphs, so the screen stays
 * short and each one is next to the thing it explains.
 */
public final class WelcomeScreen extends Screen implements GohScreen {
    public enum Page { MAIN, LUMI }

    private static final int TEXT_WIDTH = 320;
    private static final int BUTTON_WIDTH = 220;
    private static final int ROW = 22;

    private final Page page;
    private final TextPanel text = new TextPanel();

    public WelcomeScreen(Page page) {
        super(Text.literal(page == Page.LUMI ? "Meet Lumi"
                : "Welcome to " + (Session.serverName.isBlank() ? "GoH" : Session.serverName)));
        this.page = page;
    }

    @Override
    protected void init() {
        int rows = page == Page.LUMI ? 2 : 5;
        int x = (width - BUTTON_WIDTH) / 2;
        int y = height - 8 - rows * ROW;
        int textTop = 30;
        text.layout(textRenderer, page == Page.LUMI ? lumiText() : mainText(),
                Math.min(TEXT_WIDTH, width - 40), textTop, y - 8);

        if (page == Page.LUMI) {
            ClientConfig.get().lumiIntroSeen = true;
            ClientConfig.save();
            ButtonWidget hello = ButtonWidget.builder(Text.literal("Say hello to Lumi"), b -> {
                close();
                if (client != null && client.player != null) {
                    client.player.networkHandler.sendChatCommand("lumi hello");
                }
            }).dimensions(x, y, BUTTON_WIDTH, 20).build();
            hello.active = GohClient.lumiInstalled();
            if (!hello.active) hello.setMessage(Text.literal("Lumi is not installed"));
            addDrawableChild(hello);
            addDrawableChild(ButtonWidget.builder(Text.literal(Session.gohServer ? "Back" : "Got it"),
                    b -> {
                        if (Session.gohServer && client != null) client.setScreen(new WelcomeScreen(Page.MAIN));
                        else close();
                    }).dimensions(x, y + ROW, BUTTON_WIDTH, 20).build());
            return;
        }

        addDrawableChild(terrainButton(x, y));
        addDrawableChild(importButton(x, y + ROW));
        ButtonWidget share = ButtonWidget.builder(Text.literal("Share a waypoint..."),
                b -> { if (client != null) client.setScreen(new ShareScreen(this)); })
                .dimensions(x, y + 2 * ROW, BUTTON_WIDTH, 20).build();
        share.active = Session.waypointsOffered && Session.canTalk();
        share.setTooltip(Tooltip.of(Text.literal("Upload one of your waypoints, or the spot you "
                + "are standing on, to the server's list. Only what you pick is sent.")));
        addDrawableChild(share);

        ButtonWidget lumi = ButtonWidget.builder(Text.literal("Meet Lumi"),
                b -> { if (client != null) client.setScreen(new WelcomeScreen(Page.LUMI)); })
                .dimensions(x, y + 3 * ROW, BUTTON_WIDTH / 2 - 2, 20).build();
        lumi.setTooltip(Tooltip.of(Text.literal("Your in-game helper, running on Claude.")));
        addDrawableChild(lumi);
        ButtonWidget site = ButtonWidget.builder(Text.literal("Website"),
                b -> Util.getOperatingSystem().open(Session.website))
                .dimensions(x + BUTTON_WIDTH / 2 + 2, y + 3 * ROW, BUTTON_WIDTH / 2 - 2, 20).build();
        site.active = Session.website != null && Session.website.startsWith("https://");
        addDrawableChild(site);
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(x, y + 4 * ROW, BUTTON_WIDTH, 20).build());
    }

    private ButtonWidget terrainButton(int x, int y) {
        boolean on = Boolean.TRUE.equals(Terrain.receiving());
        String label;
        boolean usable = true;
        if (!Session.terrainOffered) {
            label = "Distant terrain: not offered here";
            usable = false;
        } else if (!Terrain.installed()) {
            label = "Distant terrain: needs LOD Server Support";
            usable = false;
        } else {
            label = on ? "Distant terrain: on (stop)" : "Load distant terrain...";
        }
        ButtonWidget b = ButtonWidget.builder(Text.literal(label), btn -> {
            if (client == null) return;
            if (Boolean.TRUE.equals(Terrain.receiving())) {
                GohClient.say(Actions.terrain(false), true);
                clearAndInit();
            } else {
                client.setScreen(new TerrainConfirmScreen(this));
            }
        }).dimensions(x, y, BUTTON_WIDTH, 20).build();
        b.active = usable;
        b.setTooltip(Tooltip.of(Text.literal("Sends the server's map to Voxy, so you can see far "
                + "past your render distance. Off until you choose; you can stop it any time.")));
        return b;
    }

    private ButtonWidget importButton(int x, int y) {
        String label;
        boolean usable = true;
        if (!Session.waypointsOffered) {
            label = "Shared waypoints: not offered here";
            usable = false;
        } else if (!Maps.available()) {
            label = "Shared waypoints: needs JourneyMap";
            usable = false;
        } else {
            label = "Import shared waypoints" + (Session.waypointsLoaded ? " (" + Session.waypoints.size() + ")" : "");
        }
        ButtonWidget b = ButtonWidget.builder(Text.literal(label), btn -> {
            GohClient.say(Actions.importWaypoints(), true);
            clearAndInit();
        }).dimensions(x, y, BUTTON_WIDTH, 20).build();
        b.active = usable;
        b.setTooltip(Tooltip.of(Text.literal("Adds the places players have shared to a \""
                + Maps.groupName() + "\" group in JourneyMap. Your own waypoints are not touched.")));
        return b;
    }

    private List<String> mainText() {
        List<String> out = new ArrayList<>(Session.welcome);
        out.add("You can't be hurt while this screen is open. Hover a button to see what it does; "
              + "/goh opens this again.");
        return out;
    }

    private static List<String> lumiText() {
        List<String> out = new ArrayList<>();
        out.add("Lumi is a little helper who lives in your game. You talk to him in chat, and he "
              + "answers with Claude.");
        out.add("He can see what is around you, explain what you are looking at and help you "
              + "build - and he asks before he changes anything.");
        out.add("On GoH he can also share and import waypoints, switch distant terrain on, and "
              + "run your Autopilot farms when you ask.");
        out.add("He uses your own Claude Code install and account. Say hello with /lumi hello.");
        return out;
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
