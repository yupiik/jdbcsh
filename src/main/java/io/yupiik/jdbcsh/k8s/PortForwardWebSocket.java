/*
 * Copyright (c) 2023-present - Yupiik SAS - https://www.yupiik.com
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
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
