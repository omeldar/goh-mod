package com.omeldar.goh.client.bridge;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.omeldar.goh.GohMod;
import com.omeldar.goh.client.ClientConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * A loopback MCP endpoint, so Lumi (or any Claude Code session on this machine) can use the
 * GoH tools. Bound to 127.0.0.1 only, and every call needs the token from client.json.
 * Same shape as Autopilot's bridge, so Lumi Link treats both alike.
 */
public final class ApiServer {
    private static final Gson GSON = new Gson();
    private static final String PROTOCOL = "2025-06-18";
    static final String HEADER = "X-Goh-Token";
    private static HttpServer server;
    private static int boundPort;

    private ApiServer() {}

    public static synchronized void start() {
        if (server != null) return;
        int first = ClientConfig.get().bridgePort;
        int port = first;
        try {
            server = null;
            for (int p = first; p < first + 10 && server == null; p++) {
                try {
                    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), p), 0);
                    port = p;
                } catch (java.net.BindException taken) {
                    // Someone else has it - Lumi, Autopilot, a second game. Try the next one.
                }
            }
            if (server == null) throw new java.net.BindException("ports " + first + "-" + (first + 9) + " are all taken");
            boundPort = port;
            server.createContext("/health", ApiServer::health);
            server.createContext("/mcp", ApiServer::mcp);
            server.setExecutor(Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "goh-bridge");
                t.setDaemon(true);
                return t;
            }));
            server.start();
            GohMod.LOG.info("goh bridge listening on 127.0.0.1:{}", port);
        } catch (Exception e) {
            GohMod.LOG.warn("goh bridge could not start on port {}; Lumi will not see GoH tools", port, e);
            server = null;
        }
    }

    public static synchronized int port() {
        return boundPort;
    }

    public static synchronized boolean running() {
        return server != null;
    }

    public static synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private static void health(HttpExchange ex) throws IOException {
        MinecraftClient mc = MinecraftClient.getInstance();
        JsonObject o = new JsonObject();
        o.addProperty("ok", true);
        o.addProperty("mod", "goh");
        o.addProperty("inWorld", mc != null && mc.world != null);
        respond(ex, 200, GSON.toJson(o));
    }

    private static void mcp(HttpExchange ex) throws IOException {
        if (!authorised(ex)) {
            respond(ex, 401, error(null, -32001, "bad token"));
            return;
        }
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            respond(ex, 405, error(null, -32600, "use POST"));
            return;
        }
        String body;
        try (InputStream in = ex.getRequestBody()) {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        JsonObject req;
        try {
            req = JsonParser.parseString(body).getAsJsonObject();
        } catch (Exception e) {
            respond(ex, 400, error(null, -32700, "parse error"));
            return;
        }
        String method = req.has("method") ? req.get("method").getAsString() : "";
        JsonElement id = req.get("id");
        if (id == null || id.isJsonNull()) {
            ex.sendResponseHeaders(202, -1);
            ex.close();
            return;
        }
        try {
            JsonObject result = switch (method) {
                case "initialize" -> initialize(req);
                case "tools/list" -> {
                    JsonObject r = new JsonObject();
                    r.add("tools", Mcp.toolList());
                    yield r;
                }
                case "tools/call" -> callTool(req.getAsJsonObject("params"));
                case "ping" -> new JsonObject();
                default -> null;
            };
            if (result == null) {
                respond(ex, 200, error(id, -32601, "unknown method: " + method));
                return;
            }
            JsonObject envelope = new JsonObject();
            envelope.addProperty("jsonrpc", "2.0");
            envelope.add("id", id);
            envelope.add("result", result);
            respond(ex, 200, GSON.toJson(envelope));
        } catch (Exception e) {
            GohMod.LOG.warn("goh mcp call failed", e);
            respond(ex, 200, error(id, -32603, e.getMessage() == null ? e.toString() : e.getMessage()));
        }
    }

    private static JsonObject initialize(JsonObject req) {
        String requested = PROTOCOL;
        try {
            JsonObject params = req.getAsJsonObject("params");
            if (params != null && params.has("protocolVersion")) {
                requested = params.get("protocolVersion").getAsString();
            }
        } catch (Exception ignored) {
            // Answer with our own version rather than refuse an odd handshake.
        }
        JsonObject caps = new JsonObject();
        caps.add("tools", new JsonObject());
        JsonObject info = new JsonObject();
        info.addProperty("name", "goh");
        info.addProperty("version", GohMod.version());
        JsonObject result = new JsonObject();
        result.addProperty("protocolVersion", requested);
        result.add("capabilities", caps);
        result.add("serverInfo", info);
        result.addProperty("instructions", LumiLink.HINT);
        return result;
    }

    private static JsonObject callTool(JsonObject params) throws Exception {
        String name = params.get("name").getAsString();
        JsonObject args = params.has("arguments") && params.get("arguments").isJsonObject()
                ? params.getAsJsonObject("arguments") : new JsonObject();
        JsonObject item = new JsonObject();
        item.addProperty("type", "text");
        item.addProperty("text", Mcp.call(name, args));
        JsonArray content = new JsonArray();
        content.add(item);
        JsonObject result = new JsonObject();
        result.add("content", content);
        result.addProperty("isError", false);
        return result;
    }

    private static boolean authorised(HttpExchange ex) {
        String token = ClientConfig.get().bridgeToken;
        if (token == null || token.isBlank()) return false;
        String header = ex.getRequestHeaders().getFirst(HEADER);
        if (token.equals(header)) return true;
        return token.equals(ex.getRequestHeaders().getFirst("X-Lumi-Token"));
    }

    private static String error(JsonElement id, int code, String message) {
        JsonObject err = new JsonObject();
        err.addProperty("code", code);
        err.addProperty("message", message);
        JsonObject envelope = new JsonObject();
        envelope.addProperty("jsonrpc", "2.0");
        if (id != null) envelope.add("id", id);
        envelope.add("error", err);
        return GSON.toJson(envelope);
    }

    private static void respond(HttpExchange ex, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
