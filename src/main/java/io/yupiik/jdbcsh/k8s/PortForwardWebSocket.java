package io.yupiik.jdbcsh.k8s;

import java.io.IOException;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static java.util.logging.Level.SEVERE;

public class PortForwardWebSocket implements WebSocket.Listener {
    private final SocketChannel local;
    private final Consumer<CompletionStage<?>> pendingPromiseConsumer;

    private volatile boolean skipIncrement = false;
    private final AtomicInteger messages = new AtomicInteger();
    private boolean first = true;

    public PortForwardWebSocket(final SocketChannel fwdClient, final Consumer<CompletionStage<?>> pendingPromiseConsumer) {
        this.local = fwdClient;
        this.pendingPromiseConsumer = pendingPromiseConsumer;
    }

    @Override
    public synchronized CompletionStage<?> onBinary(final WebSocket webSocket, final ByteBuffer data, final boolean last) {
        if (!skipIncrement) {
            if (messages.incrementAndGet() <= 2) {
                webSocket.request(1);
                return null;
            }
            skipIncrement = true;
        }

        if (first) {
            final var firstByte = data.get();
            if (firstByte != 0) { // channel
                final var promise = webSocket
                        .sendClose(5001, "Invalid channel byte.")
                        .whenComplete((ok, ko) -> {
                            try {
                                local.close();
                            } catch (final IOException e) {
                                logger().log(SEVERE, e, e::getMessage);
                            }
                        });
                pendingPromiseConsumer.accept(promise);
                return promise;
            }
            first = false;
        }

        while (data.hasRemaining()) {
            try {
                local.write(data);
            } catch (final IOException e) {
                logger().log(SEVERE, e, e::getMessage);
                break;
            }
        }
        webSocket.request(1);
        if (last) {
            first = true;
        }
        return null;
    }

    @Override
    public void onError(final WebSocket webSocket, final Throwable error) {
        logger().log(SEVERE, error, error::getMessage);
        onClose(webSocket, 5000, error.getMessage());
        pendingPromiseConsumer.accept(webSocket.sendClose(5000, error.getMessage()));
    }

    @Override
    public CompletionStage<?> onClose(final WebSocket webSocket, final int statusCode, final String reason) {
        try {
            if (local.isConnected()) {
                local.close();
            }
        } catch (final IOException e) {
            // no-op
        }
        logger().finest(() -> "Closing k8s ws connection.");
        return null;
    }

    private Logger logger() {
        return Logger.getLogger(PortForwardWebSocket.class.getName());
    }
}
