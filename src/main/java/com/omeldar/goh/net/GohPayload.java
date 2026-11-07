package com.omeldar.goh.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * The two packets this mod sends, one each way.
 *
 * <p>Each is a kind ("hello", "waypoints", "share", ...) and a JSON body. That is a little
 * looser than a codec per message, and deliberate: the server and client of a small community
 * mod drift a version apart all the time, and an unknown field in a JSON body is ignored where
 * a changed codec would disconnect the player.
 */
public final class GohPayload {
    private GohPayload() {}

    /** Server to client. Up to 1 MiB, which is a few thousand waypoints. */
    public record ToClient(String kind, String json) implements CustomPayload {
        public static final Id<ToClient> ID = new Id<>(Identifier.of("goh", "to_client"));
        public static final PacketCodec<RegistryByteBuf, ToClient> CODEC = PacketCodec.tuple(
                PacketCodecs.string(64), ToClient::kind,
                PacketCodecs.string(1 << 20), ToClient::json,
                ToClient::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Client to server. Small on purpose: a client only ever sends one waypoint at a time. */
    public record ToServer(String kind, String json) implements CustomPayload {
        public static final Id<ToServer> ID = new Id<>(Identifier.of("goh", "to_server"));
        public static final PacketCodec<RegistryByteBuf, ToServer> CODEC = PacketCodec.tuple(
                PacketCodecs.string(64), ToServer::kind,
                PacketCodecs.string(4096), ToServer::json,
                ToServer::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
