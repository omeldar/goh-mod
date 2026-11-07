package com.omeldar.goh.client.bridge;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.omeldar.goh.GohMod;
import com.omeldar.goh.client.ClientConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tells Lumi this mod exists, by Lumi Link v1: a descriptor file in {@code config/lumi-link/}
 * that names the loopback endpoint and its token. Lumi health-checks it and offers the tools
 * under the {@code goh_} prefix. The protocol is described in goh-autopilot's
 * docs/lumi-link.md; nothing is compiled against Lumi.
 */
public final class LumiLink {
    static final String HINT =
            "GoH is the GoH server's own mod; players may call any of this \"the GoH stuff\". "
          + "Call goh_get_status first: it says what the server offers, which family mods the "
          + "server runs (serverMods, e.g. keeper), the player's actual key bindings for every "
          + "GoH-family mod (keys), and how far the map download is (distantTerrain). "
          + "DISTANT TERRAIN is also called the map download, LOD download, Voxy terrain, "
          + "\"seeing the whole map\": LOD Server Support sends the server's explored map to Voxy "
          + "so it shows far past render distance. Off until the player chooses; the first load "
          + "of the whole map can take hours and is safe to stop at any time - what arrived "
          + "stays, turning it on again resumes. Stop it with goh_set_distant_terrain on=false, "
          + "/goh terrain off, the [Stop] link in the progress line (chat, every five minutes), "
          + "or the button on the /goh screen. Only switch it on when asked. "
          + "SETUP: the walkthrough on first join (one page per mod, then all keys, then \"Have "
          + "fun.\"); /goh setup reopens it, /goh opens the server screen. On a player's very first "
          + "join only, they cannot be hurt while the setup the server opened is on screen; "
          + "screens they open themselves later (/goh, /goh setup) are not protected. "
          + "SHARED WAYPOINTS: a server list for JourneyMap. goh_import_waypoints puts them in a "
          + "\"GoH server\" group; share or remove only the one the player asks for - uploads are "
          + "always their decision, and only the owner or the server owner can overwrite or remove "
          + "one (refusals say whose it is). Commands: /goh waypoints, /goh waypoints import, "
          + "/goh waypoints share <name>, /goh waypoints here <name>, /goh waypoints remove <name>. "
          + "If serverMods has keeper, ops can use /keeper status, /keeper lag top, /keeper afk. "
          + "Keys can be changed on the setup's keys page or in Controls; Esc while choosing "
          + "unbinds, and every key has a command too.";

    private LumiLink() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("lumi-link").resolve("goh.json");
    }

    public static void announce() {
        if (!ApiServer.running()) return;
        ClientConfig cfg = ClientConfig.get();
        try {
            JsonObject d = new JsonObject();
            d.addProperty("lumiLink", 1);
            d.addProperty("modId", "goh");
            d.addProperty("name", "GoH");
            d.addProperty("version", GohMod.version());
            d.addProperty("summary", "The GoH server's mod: shared waypoints for JourneyMap, "
                    + "distant terrain for Voxy, and what the server offers.");
            d.addProperty("prefix", "goh");
            d.addProperty("mcpUrl", "http://127.0.0.1:" + ApiServer.port() + "/mcp");
            d.addProperty("healthUrl", "http://127.0.0.1:" + ApiServer.port() + "/health");
            d.addProperty("tokenHeader", ApiServer.HEADER);
            d.addProperty("token", cfg.bridgeToken);
            d.addProperty("hint", HINT);
            JsonArray tools = new JsonArray();
            for (var el : Mcp.toolList()) tools.add(el.getAsJsonObject().get("name").getAsString());
            d.add("tools", tools);
            Files.createDirectories(file().getParent());
            Files.writeString(file(), new GsonBuilder().setPrettyPrinting().create().toJson(d),
                    StandardCharsets.UTF_8);
            GohMod.LOG.info("announced to lumi-link at {}", file());
        } catch (Exception e) {
            GohMod.LOG.warn("could not write the lumi-link descriptor", e);
        }
    }

    public static void withdraw() {
        try {
            Files.deleteIfExists(file());
        } catch (Exception ignored) {
            // A stale file only fails Lumi's health check.
        }
    }
}
