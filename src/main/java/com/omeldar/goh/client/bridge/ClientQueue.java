package com.omeldar.goh.client.bridge;

import net.minecraft.client.MinecraftClient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Runs a piece of work on the client thread and waits for its answer. The bridge is served
 * by its own thread, and game state is only safe to read or change from the client's.
 */
final class ClientQueue {
    private ClientQueue() {}

    static <T> T on(Function<MinecraftClient, T> work) throws Exception {
        MinecraftClient mc = MinecraftClient.getInstance();
        CompletableFuture<T> future = new CompletableFuture<>();
        mc.execute(() -> {
            try {
                future.complete(work.apply(mc));
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future.get(15, TimeUnit.SECONDS);
    }
}
