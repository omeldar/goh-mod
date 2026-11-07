package com.omeldar.goh.client;

import com.omeldar.goh.client.ui.SetupScreen;
import com.omeldar.goh.client.ui.WelcomeScreen;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * The hub's own keys. Only the screen key gets a default - G, which vanilla leaves free - so a
 * fresh install has one key that is definitely not already taken. The other two start unbound
 * and the setup walkthrough offers them, along with every other GoH-family key, on its last page.
 */
public final class Keybinds {
    private static KeyBinding open;
    private static KeyBinding setup;
    private static KeyBinding lumi;

    private Keybinds() {}

    public static void register() {
        open = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.goh.open", GLFW.GLFW_KEY_G, KeyBinding.Category.MISC));
        setup = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.goh.setup", InputUtil.UNKNOWN_KEY.getCode(), KeyBinding.Category.MISC));
        lumi = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.goh.lumi", InputUtil.UNKNOWN_KEY.getCode(), KeyBinding.Category.MISC));
    }

    public static void onTick(MinecraftClient mc) {
        if (open != null) {
            while (open.wasPressed()) {
                if (mc.currentScreen == null && mc.player != null) {
                    mc.setScreen(new WelcomeScreen(WelcomeScreen.Page.MAIN));
                }
            }
        }
        if (setup != null) {
            while (setup.wasPressed()) {
                if (mc.currentScreen == null && mc.player != null) mc.setScreen(new SetupScreen());
            }
        }
        if (lumi != null) {
            while (lumi.wasPressed()) {
                if (mc.currentScreen != null || mc.player == null) continue;
                if (GohClient.lumiInstalled()) {
                    mc.player.networkHandler.sendChatCommand("lumi hello");
                } else {
                    GohClient.say("Lumi is not installed", false);
                }
            }
        }
    }
}
