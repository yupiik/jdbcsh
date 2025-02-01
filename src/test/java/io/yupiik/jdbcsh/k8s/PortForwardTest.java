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

import com.sun.net.httpserver.HttpServer;
import io.yupiik.fusion.json.internal.JsonMapperImpl;
import io.yupiik.fusion.kubernetes.client.KubernetesClient;
import io.yupiik.fusion.kubernetes.client.KubernetesClientConfiguration;
import io.yupiik.jdbcsh.configuration.KubernetesPortForwardConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Optional.empty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortForwardTest {
    @Test
    void forward() throws IOException {
        assertTrue(doRun("test", null, null).isEmpty());
    }

    @Test
    void forwardFromPrefix() throws IOException {
        assertEquals(
                List.of("/api/v1/namespaces/ns/pods?limit=1000&labelSelector=app=junit"),
                doRun(null, "tes", null));
    }

    @Test
    void forwardFromService() throws IOException {
        assertEquals(
                List.of("/api/v1/namespaces/ns/services/database", "/api/v1/namespaces/ns/pods?limit=1000&labelSelector=app=test"),
                doRun(null, null, "database"));
    }

    private List<String> doRun(final String pod, final String podPrefix, final String service) throws IOException {
        final var requests = new ArrayList<String>();
        final var mock = k8sMock(requests);
        final var success = new AtomicInteger(0);
        try {
            final var k8sApi = "http://localhost:" + mock.getAddress().getPort();
            final var forward = new PortForward(
                    new KubernetesPortForwardConfiguration(
                            null, null, null, null, "",
                            false, k8sApi,
                            1234,
                            "localhost", 0,
                            pod, podPrefix, service, "ns", "app=junit"),
                    "junit-connection",
                    new JsonMapperImpl(List.of(), k -> empty())) {
                @Override
                protected LocalProxy newLocalProxy(final KubernetesClient client, final URI uri) throws IOException {
                    assertEquals("wss://kubernetes.api/api/v1/namespaces/ns/pods/test/portforward?ports=1234", uri.toASCIIString());
                    success.incrementAndGet();
                    return super.newLocalProxy(client, uri);
                }

                @Override
                protected KubernetesClient newK8SClient() {
                    return new KubernetesClient(new KubernetesClientConfiguration().setMaster(k8sApi));
                }
            };
            try (final var ctx = forward.launch()) {
                assertNotEquals(0, ctx.proxy().localAddress().getPort());
            } catch (final Exception e) {
                throw new IllegalStateException(e);
            }
            assertEquals(1, success.get());
        } finally {
            mock.stop(0);
        }
        return requests;
    }

    private HttpServer k8sMock(final List<String> requests) throws IOException {
        final var server = HttpServer.create(new InetSocketAddress(0), 64);
        server.createContext("/").setHandler(ex -> {
            assertEquals("GET", ex.getRequestMethod());

            final var uri = ex.getRequestURI();
            final var path = uri.getPath();
            synchronized (requests) {
                requests.add(path + (uri.getQuery() != null ? '?' + uri.getQuery() : ""));
            }

            try (ex) {
                switch (path) {
                    case "/api/v1/namespaces/ns/pods" -> {
                        final var out = """
                                {
                                  "items":[
                                    {
                                      "metadata": {"name":"test"}
                                    }
                                  ]
                                }""".getBytes(StandardCharsets.UTF_8);
                        ex.sendResponseHeaders(200, out.length);
                        ex.getResponseBody().write(out);
                    }
                    case "/api/v1/namespaces/ns/services/database" -> {
                        final var out = """
                                {
                                  "spec":{
                                    "selector":{
                                      "app": "test"
                                    }
                                  }
                                }""".getBytes(StandardCharsets.UTF_8);
                        ex.sendResponseHeaders(200, out.length);
                        ex.getResponseBody().write(out);
                    }
                    default -> ex.sendResponseHeaders(404, 0);
                }
            }
        });
        server.start();
        return server;
    }
}
