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

import org.junit.jupiter.api.Test;

import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PortForwardWebSocketTest {
    @Test
    void test() {
        final var written = new ArrayList<byte[]>();
        final var ws = new PortForwardWebSocket(newChannel(written), i -> {
        });
        final var requested = new AtomicInteger();
        final var websocket = newWebSocket(requested);
        assertEquals(0, requested.get());
        ws.onBinary(websocket, ByteBuffer.wrap(new byte[]{0, 1, 1, 1}), true);
        assertEquals(1, requested.get());
        ws.onBinary(websocket, ByteBuffer.wrap(new byte[]{0, 2, 2, 2}), true);
        assertEquals(2, requested.get());
        ws.onBinary(websocket, ByteBuffer.wrap(new byte[]{0, 3, 3, 3}), true);
        assertEquals(3, requested.get());
        assertEquals(1, written.size());
        assertArrayEquals(new byte[]{3, 3, 3}, written.get(0));
    }

    private WebSocket newWebSocket(final AtomicInteger requested) {
        return new WebSocket() {
            @Override
            public CompletableFuture<WebSocket> sendText(final CharSequence data, final boolean last) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<WebSocket> sendBinary(final ByteBuffer data, final boolean last) {
                throw new UnsupportedOperationException();
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
                throw new UnsupportedOperationException();
            }

            @Override
            public void request(final long n) {
                requested.incrementAndGet();
            }

            @Override
            public String getSubprotocol() {
                return null;
            }

            @Override
            public boolean isOutputClosed() {
                return false;
            }

            @Override
            public boolean isInputClosed() {
                return false;
            }

            @Override
            public void abort() {
                // no-op
            }
        };
    }

    private SocketChannel newChannel(final List<byte[]> written) {
        return new SocketChannel(null) {
            @Override
            public int write(final ByteBuffer src) {
                final var bytes = new byte[src.remaining()];
                synchronized (written) {
                    src.get(bytes);
                    written.add(bytes);
                }
                return bytes.length;
            }

            @Override
            public boolean isConnected() {
                return true;
            }

            @Override
            public SocketChannel bind(final SocketAddress local) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> SocketChannel setOption(final SocketOption<T> name, final T value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public SocketChannel shutdownInput() {
                throw new UnsupportedOperationException();
            }

            @Override
            public SocketChannel shutdownOutput() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Socket socket() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isConnectionPending() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean connect(final SocketAddress remote) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean finishConnect() {
                throw new UnsupportedOperationException();
            }

            @Override
            public SocketAddress getRemoteAddress() {
                throw new UnsupportedOperationException();
            }

            @Override
            public int read(final ByteBuffer dst) {
                throw new UnsupportedOperationException();
            }

            @Override
            public long read(final ByteBuffer[] dsts, final int offset, final int length) {
                throw new UnsupportedOperationException();
            }

            @Override
            public long write(final ByteBuffer[] srcs, final int offset, final int length) {
                throw new UnsupportedOperationException();
            }

            @Override
            public SocketAddress getLocalAddress() {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> T getOption(final SocketOption<T> name) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Set<SocketOption<?>> supportedOptions() {
                throw new UnsupportedOperationException();
            }

            @Override
            protected void implCloseSelectableChannel() {
                throw new UnsupportedOperationException();
            }

            @Override
            protected void implConfigureBlocking(final boolean block) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
