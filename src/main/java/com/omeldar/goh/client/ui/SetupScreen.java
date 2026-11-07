package com.omeldar.goh.client.ui;

import com.omeldar.goh.client.ClientConfig;
import com.omeldar.goh.client.GohClient;
import com.omeldar.goh.client.Session;
import com.omeldar.goh.client.Terrain;
import com.omeldar.goh.client.map.Maps;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The walkthrough a player gets the first time they join: one page per mod, each explaining what
 * the thing does and offering the one choice that mod needs, then a page that binds every key in
 * the family, then a send-off.
 *
 * <p>Pages are built at construction and only for what is actually installed - a player without
 * JourneyMap never sees a waypoints page - so the length of the walkthrough matches the size of
 * the install rather than the size of the project.
 *
 * <p>Laid out from the bottom up like the other GoH screens: navigation first, then the page's
 * own controls, and the prose takes whatever height is left and scrolls.
 */
public final class SetupScreen extends Screen implements GohScreen {
    /** Key ids this screen will rebind. Matched by prefix, not category, since a mod can be absent. */
    private static final String[] FAMILY = {"key.goh.", "key.planner.", "key.autopilot.", "key.lumi."};

    private static final int TEXT_WIDTH = 330;
    private static final int NARROW = 220;
    private static final int WIDE = 290;
    private static final int ROW = 22;

    private final List<Step> steps = new ArrayList<>();
    private final TextPanel text = new TextPanel();

    private int index;
    private KeyBinding listening;
    private KeyBinding lastChanged;
    private boolean greetLumi;
    private int noticeY;

    public SetupScreen() {
        super(Text.literal("Setup"));
        steps.add(new Intro());
        if (Session.terrainOffered) steps.add(new TerrainStep());
        if (Session.waypointsOffered) steps.add(new WaypointStep());
        if (loaded("planner")) steps.add(new PlannerStep());
        if (loaded("autopilot")) steps.add(new AutopilotStep());
        if (GohClient.lumiInstalled()) steps.add(new LumiStep());
        if (!familyKeys().isEmpty()) steps.add(new KeyStep());
        steps.add(new Done());
    }

    private static boolean loaded(String id) {
        return FabricLoader.getInstance().isModLoaded(id);
    }

    private Step current() {
        return steps.get(Math.max(0, Math.min(index, steps.size() - 1)));
    }

    @Override
    protected void init() {
        Step step = current();
        int w = Math.min(step.wide() ? WIDE : NARROW, width - 40);
        int x = (width - w) / 2;
        int navY = height - 26;
        noticeY = navY - 12;
        int contentTop = noticeY - 4 - step.rows() * ROW;

        text.layout(textRenderer, step.body(), Math.min(TEXT_WIDTH, width - 40), 42, contentTop - 6);
        step.controls(this, x, contentTop, w);

        int half = w / 2 - 2;
        addDrawableChild(ButtonWidget.builder(Text.literal(step.backLabel()), b -> back())
                .dimensions(x, navY, half, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(step.nextLabel()), b -> next())
                .dimensions(x + w - half, navY, half, 20).build());
    }

    private void back() {
        if (index == 0) {
            close();
            return;
        }
        index--;
        step();
    }

    private void next() {
        if (index >= steps.size() - 1) {
            finish();
            return;
        }
        index++;
        step();
    }

    private void step() {
        listening = null;
        lastChanged = null;
        clearAndInit();
    }

    private void finish() {
        ClientConfig.get().setupDone = true;
        ClientConfig.save();
        if (greetLumi && client != null && client.player != null) {
            client.player.networkHandler.sendChatCommand("lumi hello");
        }
        close();
    }

    // Pages reach the screen through these rather than through the protected members they would
    // otherwise inherit from Screen, which are awkward to touch from a nested class.

    void add(ButtonWidget widget) {
        addDrawableChild(widget);
    }

    void refresh() {
        clearAndInit();
    }

    MinecraftClient mc() {
        return client;
    }

    // ---- keys -------------------------------------------------------------------------

    /** Every registered key belonging to a GoH-family mod, in the order the game holds them. */
    private static List<KeyBinding> familyKeys() {
        List<KeyBinding> out = new ArrayList<>();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null) return out;
        for (KeyBinding binding : mc.options.allKeys) {
            for (String prefix : FAMILY) {
                if (binding.getId().startsWith(prefix)) {
                    out.add(binding);
                    break;
                }
            }
        }
        return out;
    }

    /** What else in the game answers to this key. Vanilla bindings included, deliberately. */
    private List<String> clashes(KeyBinding binding) {
        List<String> out = new ArrayList<>();
        if (binding.isUnbound() || client == null || client.options == null) return out;
        for (KeyBinding other : client.options.allKeys) {
            if (other != binding && binding.equals(other)) {
                out.add(Text.translatable(other.getId()).getString());
            }
        }
        return out;
    }

    private ButtonWidget keyRow(KeyBinding binding, int x, int y, int w) {
        List<String> clash = clashes(binding);
        MutableText label = Text.translatable(binding.getId()).copy().formatted(Formatting.WHITE)
                .append(Text.literal("   "));
        if (listening == binding) {
            label.append(Text.literal("> press a key <").formatted(Formatting.YELLOW));
        } else if (binding.isUnbound()) {
            label.append(Text.literal("unbound").formatted(Formatting.DARK_GRAY));
        } else {
            label.append(binding.getBoundKeyLocalizedText().copy()
                    .formatted(clash.isEmpty() ? Formatting.AQUA : Formatting.RED));
        }

        ButtonWidget button = ButtonWidget.builder(label, b -> {
            listening = binding;
            lastChanged = null;
            clearAndInit();
        }).dimensions(x, y, w, 20).build();

        if (!clash.isEmpty()) {
            button.setTooltip(Tooltip.of(Text.literal("Already " + join(clash)
                    + ". Both will still fire - pick another key, or press Esc while it is "
                    + "listening to clear this one.")));
        } else if (binding.isUnbound()) {
            button.setTooltip(Tooltip.of(Text.literal(
                    "No key. The matching command still works if you would rather type.")));
        }
        return button;
    }

    private void bind(InputUtil.Key key) {
        KeyBinding target = listening;
        listening = null;
        if (target == null) return;
        target.setBoundKey(key);
        KeyBinding.updateKeysByCode();
        if (client != null && client.options != null) client.options.write();
        lastChanged = target;
        clearAndInit();
    }

    private static String join(List<String> names) {
        if (names.size() == 1) return names.get(0);
        return String.join(", ", names.subList(0, names.size() - 1))
                + " and " + names.get(names.size() - 1);
    }

    /** The line under the page: what is being asked for, or what a just-bound key already does. */
    private Text notice() {
        if (listening != null) {
            return Text.literal("Press any key or mouse button. Esc clears it instead.")
                    .formatted(Formatting.YELLOW);
        }
        if (lastChanged == null) return null;
        if (lastChanged.isUnbound()) {
            return Text.literal("Cleared - " + Text.translatable(lastChanged.getId()).getString()
                    + " has no key now, and its command still works.").formatted(Formatting.GRAY);
        }
        List<String> clash = clashes(lastChanged);
        if (clash.isEmpty()) return null;
        return Text.literal(lastChanged.getBoundKeyLocalizedText().getString()
                + " is also " + join(clash) + ". Click the row again to pick another.")
                .formatted(Formatting.RED);
    }

    // ---- input ------------------------------------------------------------------------

    @Override
    public boolean keyPressed(KeyInput input) {
        if (listening != null) {
            bind(input.key() == GLFW.GLFW_KEY_ESCAPE
                    ? InputUtil.UNKNOWN_KEY
                    : InputUtil.Type.KEYSYM.createFromCode(input.key()));
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (listening != null) {
            bind(InputUtil.Type.MOUSE.createFromCode(click.button()));
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        return text.scroll(vertical) || super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        Step step = current();
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal(step.title()).formatted(Formatting.BOLD), width / 2, 14, 0xFF55FFFF);

        String accent = step.accent();
        if (accent != null) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(accent).formatted(Formatting.BOLD), width / 2, 29, 0xFFFFC14D);
        } else {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Step " + (index + 1) + " of " + steps.size()),
                    width / 2, 29, 0xFF8899AA);
        }

        text.render(ctx, textRenderer, width / 2, width);

        Text notice = notice();
        if (notice != null) {
            ctx.drawCenteredTextWithShadow(textRenderer, notice, width / 2, noticeY, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ---- pages ------------------------------------------------------------------------

    private abstract static class Step {
        abstract String title();

        abstract List<String> body();

        /** Button rows this page needs under its prose. */
        int rows() {
            return 0;
        }

        boolean wide() {
            return false;
        }

        /** Drawn under the title in place of the step counter. */
        String accent() {
            return null;
        }

        String backLabel() {
            return "Back";
        }

        String nextLabel() {
            return "Next";
        }

        void controls(SetupScreen screen, int x, int y, int w) {
        }
    }

    private static String serverName() {
        return Session.serverName.isBlank() ? "GoH" : Session.serverName;
    }

    private static final class Intro extends Step {
        @Override
        String title() {
            return "Welcome to " + serverName();
        }

        @Override
        String backLabel() {
            return "Skip for now";
        }

        @Override
        String nextLabel() {
            return "Start";
        }

        @Override
        List<String> body() {
            List<String> out = new ArrayList<>(Session.welcome);
            out.add("Worth two minutes of your time: this server runs a handful of mods that "
                  + "change how the game actually plays - how far you can see, what your map "
                  + "knows, and what you can hand off while you go do something else.");
            out.add("The next few pages take them one at a time. Each one says what it does and "
                  + "gives you the choice it needs, and the last one puts every key in a single "
                  + "list so you never have to go digging through Controls.");
            out.add((GohClient.forcedSetup() ? "Nothing can hurt you while this first setup is open, and "
                    : "") + "/goh setup brings it back whenever you want another look.");
            return out;
        }
    }

    private static final class TerrainStep extends Step {
        @Override
        String title() {
            return "Distant terrain";
        }

        @Override
        int rows() {
            return 1;
        }

        @Override
        boolean wide() {
            return true;
        }

        @Override
        List<String> body() {
            List<String> out = new ArrayList<>();
            out.add("Normally the world stops at your render distance. Voxy, with LOD Server "
                  + "Support, keeps its own copy of the server's map and draws everything past "
                  + "that: mountains on the horizon, your base from three thousand blocks out.");
            out.add("The server hands it over once and then it lives on your disk. It is a real "
                  + "download - closer to gigabytes than megabytes - which is why it stays off "
                  + "until you ask for it. You can stop part-way and keep whatever arrived.");
            if (Session.terrainColumns > 0) {
                out.add("This server has roughly " + String.format(java.util.Locale.ROOT, "%,d", Session.terrainColumns) + " columns to send.");
            }
            if (!Terrain.installed()) {
                out.add("Voxy and LOD Server Support are not installed, so the button below is "
                      + "greyed out. Add them and it wakes up.");
            }
            return out;
        }

        @Override
        void controls(SetupScreen screen, int x, int y, int w) {
            screen.add(FeatureButtons.terrain(screen.mc(), screen, x, y, w, screen::refresh));
        }
    }

    private static final class WaypointStep extends Step {
        @Override
        String title() {
            return "Shared waypoints";
        }

        @Override
        int rows() {
            return 1;
        }

        @Override
        boolean wide() {
            return true;
        }

        @Override
        List<String> body() {
            List<String> out = new ArrayList<>();
            out.add("Players here share the places worth knowing: the gold farm, the portal that "
                  + "goes somewhere useful, the stronghold somebody already walked to so that you "
                  + "do not have to.");
            out.add("Importing drops them into their own group in JourneyMap, alongside your own. "
                  + "Your waypoints are never touched, and nothing of yours leaves this machine "
                  + "unless you pick it and share it yourself.");
            if (!Maps.available()) {
                out.add("JourneyMap is not installed, so there is nowhere to put them yet.");
            }
            return out;
        }

        @Override
        void controls(SetupScreen screen, int x, int y, int w) {
            screen.add(FeatureButtons.importWaypoints(x, y, w, screen::refresh));
        }
    }

    private static final class PlannerStep extends Step {
        @Override
        String title() {
            return "The planner";
        }

        @Override
        List<String> body() {
            return List.of(
                "A big build is a shopping list you cannot hold in your head. The planner keeps "
                  + "that list in the units you actually store things in: stacks, shulker boxes, "
                  + "double chests, and double chests full of shulkers.",
                "Add what the build needs, press plus as you fill each container, and it tells you "
                  + "what is left and roughly how long the rest will take at the rate your farm "
                  + "is running.",
                "It opens with B out of the box, or /plan if you would rather type. You can move "
                  + "that key on the last page.");
        }
    }

    private static final class AutopilotStep extends Step {
        @Override
        String title() {
            return "Autopilot";
        }

        @Override
        List<String> body() {
            return List.of(
                "Record a farm run once and let it replay on a loop while you do something else. "
                  + "It is your own route played back, not a bot that knows the world.",
                "It stops the instant you touch a movement key, and End stops it outright. That "
                  + "one comes bound by default on purpose: a stop you have to go and bind first "
                  + "is not a stop.",
                "Starting a recording has no key yet. The last page is where you give it one.");
        }
    }

    private static final class LumiStep extends Step {
        @Override
        String title() {
            return "Lumi";
        }

        @Override
        int rows() {
            return 1;
        }

        @Override
        boolean wide() {
            return true;
        }

        @Override
        List<String> body() {
            return List.of(
                "Lumi is a helper who lives in your game and answers through Claude. You talk to "
                  + "him in chat, in normal words.",
                "He can look at what is around you, explain what you are staring at, and help you "
                  + "build. He asks before he changes anything, every time.",
                "Here he can also share and import waypoints, switch distant terrain on, and run "
                  + "your Autopilot farms when you ask him to.",
                "He runs on your own Claude Code install and account, so nothing goes anywhere you "
                  + "have not already signed in to.");
        }

        @Override
        void controls(SetupScreen screen, int x, int y, int w) {
            screen.add(ButtonWidget.builder(
                    Text.literal("Say hello when I finish: " + (screen.greetLumi ? "yes" : "no")),
                    b -> {
                        screen.greetLumi = !screen.greetLumi;
                        screen.refresh();
                    }).dimensions(x, y, w, 20).build());
        }
    }

    private static final class KeyStep extends Step {
        @Override
        String title() {
            return "Your keys";
        }

        @Override
        boolean wide() {
            return true;
        }

        @Override
        int rows() {
            return familyKeys().size();
        }

        @Override
        List<String> body() {
            return List.of(
                "Everything these mods do is one key away, and they are all here so you do not "
                  + "have to go hunting through Controls for them afterwards.",
                "Click a row, then press the key you want. Esc while it is listening clears the "
                  + "key instead - useful if you would rather drive everything from commands.",
                "If a key is already doing something else, it says so and leaves the decision to "
                  + "you. Nothing is blocked; two things on one key is occasionally the point.");
        }

        @Override
        void controls(SetupScreen screen, int x, int y, int w) {
            List<KeyBinding> keys = familyKeys();
            for (int i = 0; i < keys.size(); i++) {
                screen.add(screen.keyRow(keys.get(i), x, y + i * ROW, w));
            }
        }
    }

    private static final class Done extends Step {
        @Override
        String title() {
            return "Welcome to " + serverName();
        }

        @Override
        String accent() {
            return "Have fun.";
        }

        @Override
        String nextLabel() {
            return "Let's play";
        }

        @Override
        List<String> body() {
            return List.of(
                "That is the lot. Nothing here is permanent - every switch has an off, and every "
                  + "key can move.",
                "/goh opens the server screen, /plan opens the planner, and /goh setup walks "
                  + "through all of this again whenever you like.",
                "See you out there.");
        }
    }
}
