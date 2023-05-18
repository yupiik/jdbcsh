package io.yupiik.jdbcsh.k8s;

import io.yupiik.fusion.kubernetes.client.KubernetesClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.logging.Level.SEVERE;

public class LocalProxy implements AutoCloseable {
    private final Logger logger = Logger.getLogger(LocalProxy.class.getName());

    private final ServerSocketChannel socket;
    private final Map<SocketChannel, Connection> clients = new ConcurrentHashMap<>();
    private final ExecutorService threads;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public LocalProxy(final String address, final int inPort, final KubernetesClient client, final URI uri) throws IOException {
        final var selector = Selector.open();
        socket = ServerSocketChannel.open();
        threads = Executors.newCachedThreadPool(new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger();

            @Override
            public Thread newThread(final Runnable task) {
                return new Thread(task, LocalProxy.class.getName() + "-pool-" + counter.incrementAndGet());
            }
        });

        socket.bind(new InetSocketAddress(address == null ? "localhost" : address, inPort));
        socket.configureBlocking(false);
        socket.register(selector, SelectionKey.OP_ACCEPT, null);
        threads.execute(() -> eventLoop(client, uri, selector));
    }

    private void eventLoop(final KubernetesClient client, final URI uri, final Selector selector) {
        while (isRunning()) {
            try {
                selector.select();
                final var selectedKeys = selector.selectedKeys();
                for (final var key : selectedKeys) {
                    if (!key.isValid()) {
                        continue;
                    }
                    if (key.isAcceptable()) {
                        onAccept(
                                client.newWebSocketBuilder().subprotocols("v4.channel.k8s.io"),
                                uri, selector, socket, clients);
                    }
                    if (key.isReadable()) {
                        onRead(clients, key);
                    }
                }
                selectedKeys.clear();
            } catch (final IOException ioe) {
                logger.log(SEVERE, ioe, ioe::getMessage);
            }
        }
        logger.finest(() -> "Exiting proxy event loop");
    }

    private boolean isRunning() {
        return running.get() && !Thread.currentThread().isInterrupted() && !threads.isShutdown();
    }

    private void onRead(final Map<SocketChannel, Connection> clients, final SelectionKey key) {
        try {
            final var fwsClient = (SocketChannel) key.channel();
            final var connection = clients.get(fwsClient);
            if (connection != null) {
                final var buffer = connection.buffer();
                synchronized (buffer) {
                    buffer.clear();
                    buffer.put((byte) 0); // channel byte
                    final int read = fwsClient.read(buffer);
                    if (read > 0) {
                        buffer.flip();
                        connection.registerPending(connection.webSocket()
                                .thenCompose(ws -> ws
                                        .sendBinary(buffer, true)
                                        .exceptionally(e -> {
                                            logger.log(SEVERE, e, e::getMessage);
                                            return null;
                                        })));
                    } else if (read == -1) {
                        logger.finest(() -> "Closing client " + fwsClient);
                        clients.remove(fwsClient);
                        connection.registerPending(connection.webSocket()
                                .thenCompose(ws -> ws.sendClose(WebSocket.NORMAL_CLOSURE, "Bye."))
                                .whenComplete((ok, ko) -> clients.remove(fwsClient)));
                    }
                }
            } else {
                logger.warning(() -> "No k8s client for " + fwsClient + " skipping read key");
            }
        } catch (final IOException e) {
            logger.log(SEVERE, e, e::getMessage);
        }
    }

    private void onAccept(final WebSocket.Builder req, final URI uri, final Selector selector, final ServerSocketChannel socket,
                          final Map<SocketChannel, Connection> clients) {
        try {
            final var fwdClient = socket.accept();
            fwdClient.configureBlocking(false);
            fwdClient.register(selector, SelectionKey.OP_READ);

            final var wsPromise = new CompletableFuture<WebSocket>();
            final var connection = new Connection(wsPromise, ByteBuffer.allocate(4096), new CopyOnWriteArrayList<>());
            final var listener = new PortForwardWebSocket(fwdClient, connection::registerPending);
            req.buildAsync(uri, listener).whenComplete((ok, ko) -> {
                if (ko != null) {
                    clients.remove(fwdClient);
                    logger.log(SEVERE, ko, ko::getMessage);
                    wsPromise.completeExceptionally(ko);
                } else {
                    wsPromise.complete(ok);
                }
            });

            clients.put(fwdClient, connection);
        } catch (final IOException e) {
            logger.log(SEVERE, e, e::getMessage);
        }
    }

    @Override
    public void close() throws IOException {
        running.set(false);
        try {
            socket.close();
        } finally {
            try {
                allNoFailFast(clients.values().stream()
                        .filter(it -> !it.pending().isEmpty())
                        .map(it -> allNoFailFast(it.pending()))
                        .toList())
                        .get();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (final ExecutionException e) {
                logger.log(SEVERE, e, e::getMessage);
            }

            threads.shutdownNow();
            try {
                if (!threads.awaitTermination(1, MINUTES)) {
                    logger.warning("Can't stop thread pool in 1mn");
                }
            } catch (final InterruptedException e) {
                logger.log(SEVERE, e, e::getMessage);
                Thread.currentThread().interrupt();
            }
        }
    }

    private CompletableFuture<?> allNoFailFast(final List<? extends CompletionStage<?>> list) {
        CompletableFuture<?> first = completedFuture(null);
        for (final var promise : list) {
            first = first.thenCompose(ignored -> promise.exceptionally(e -> null));
        }
        return first.toCompletableFuture();
    }

    public InetSocketAddress localAddress() throws IOException {
        return (InetSocketAddress) socket.getLocalAddress();
    }

    private record Connection(CompletionStage<WebSocket> webSocket, ByteBuffer buffer,
                              List<CompletionStage<?>> pending) {
        private void registerPending(final CompletionStage<?> promise) {
            pending().add(promise);
            promise.whenComplete((ok, ko) -> pending().remove(promise));
        }
    }
}
