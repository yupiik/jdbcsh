/*
 * Copyright (c) 2023 - Yupiik SAS - https://www.yupiik.com
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

import io.yupiik.fusion.kubernetes.client.KubernetesClient;
import io.yupiik.fusion.kubernetes.client.KubernetesClientConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalProxyTest {
    @Test
    void forward() throws IOException, InterruptedException {
        final var fakeWss = URI.create("ws://localhost:1234/test");
        final var ws = new CopyOnWriteArrayList<InMemoryWebSocket>();
        final var latch = new Semaphore(0);
        try (final var proxy = new LocalProxy(
                "localhost", 0,
                new KubernetesClient(new KubernetesClientConfiguration()
                        .setMaster("http://localhost:-1/master")) {
                    @Override
                    public WebSocket.Builder newWebSocketBuilder() {
                        final var socket = new InMemoryWebSocket(latch::release);
                        ws.add(socket);
                        return socket;
                    }
                },
                fakeWss)) {
            final var server = proxy.localAddress();
            assertNotEquals(0, server.getPort());

            assertEquals(0, ws.size());
            try (final var client = new Socket(server.getAddress(), server.getPort())) {
                assertTrue(latch.tryAcquire(1, MINUTES));
                assertEquals(1, ws.size());

                final var first = ws.get(0);
                assertFalse(first.closed);
                assertEquals("v4.channel.k8s.io", first.subprotocol);
                assertEquals(0, first.binary.size());
                assertEquals(fakeWss, first.uri);
                assertNotNull(first.listener);

                final var outputStream = client.getOutputStream();
                outputStream.write("hello".getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
                assertTrue(latch.tryAcquire(1, MINUTES));
                assertEquals(1, first.binary.size());

                final var binary = first.binary.iterator().next();
                assertEquals(0, binary[0]);
                assertEquals("hello", new String(binary, 1, binary.length - 1, StandardCharsets.UTF_8));
            }

            assertTrue(latch.tryAcquire(1, MINUTES));
            assertTrue(ws.get(0).closed);
        }
    }

    private static class InMemoryWebSocket implements WebSocket.Builder, WebSocket {
        private final Runnable onAction;
        private String subprotocol;
        private URI uri;
        private WebSocket.Listener listener;
        private volatile boolean closed;
        private final Collection<byte[]> binary = new CopyOnWriteArrayList<>();

        private InMemoryWebSocket(final Runnable onAction) {
            this.onAction = onAction;
        }

        @Override
        public WebSocket.Builder header(final String name, final String value) {
            return this;
        }

        @Override
        public WebSocket.Builder connectTimeout(final Duration timeout) {
            return this;
        }

        @Override
        public WebSocket.Builder subprotocols(final String mostPreferred, final String... lesserPreferred) {
            this.subprotocol = mostPreferred;
            return this;
        }

        @Override
        public CompletableFuture<WebSocket> buildAsync(final URI uri, final WebSocket.Listener listener) {
            this.uri = uri;
            this.listener = listener;
            this.onAction.run();
            return completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendText(final CharSequence data, final boolean last) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<WebSocket> sendBinary(final ByteBuffer data, final boolean last) {
            final var bytes = new byte[data.remaining()];
            data.get(bytes);
            this.binary.add(bytes);
            this.onAction.run();
            return completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendPing(final ByteBuffer message) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<WebSocket> sendPong(final ByteBuffer message) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<WebSocket> sendClose(final int statusCode, final String reason) {
            this.closed = true;
            onAction.run();
            return completedFuture(this);
        }

        @Override
        public void request(final long n) {
            // no-op
        }

        @Override
        public String getSubprotocol() {
            return subprotocol;
        }

        @Override
        public boolean isOutputClosed() {
            return closed;
        }

        @Override
        public boolean isInputClosed() {
            return closed;
        }

        @Override
        public void abort() {
            // no-op
        }
    }
}
