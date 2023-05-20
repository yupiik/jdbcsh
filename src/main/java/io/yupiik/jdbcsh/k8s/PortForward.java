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

import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.kubernetes.client.KubernetesClient;
import io.yupiik.fusion.kubernetes.client.KubernetesClientConfiguration;
import io.yupiik.jdbcsh.configuration.KubernetesPortForwardConfiguration;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import static java.net.http.HttpResponse.BodyHandlers.ofByteArray;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;

// see github.com/kubernetes/kubernetes/pull/33684
public class PortForward {
    private final KubernetesPortForwardConfiguration configuration;
    private final String connectionName;
    private final JsonMapper jsonMapper;

    public PortForward(final KubernetesPortForwardConfiguration configuration,
                       final String connectionName,
                       final JsonMapper jsonMapper) {
        this.configuration = configuration;
        this.connectionName = connectionName;
        this.jsonMapper = jsonMapper;
    }

    public ForwardingContext launch() {
        final var client = newK8SClient();
        try {
            final var namespace = ofNullable(this.configuration.namespace()).or(client::namespace).orElse("default");
            final var pod = findPod(client, namespace);
            final var uri = URI.create("wss://kubernetes.api" +
                    "/api/v1/namespaces/" + namespace +
                    "/pods/" + pod +
                    "/portforward?ports=" + configuration.containerPort());

            try {
                return new ForwardingContext(client, newLocalProxy(client, uri));
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        } catch (final RuntimeException re) {
            client.close();
            throw re;
        }
    }

    protected LocalProxy newLocalProxy(final KubernetesClient client, final URI uri) throws IOException {
        return new LocalProxy(configuration.localAddress(), configuration.localPort(), client, uri);
    }

    // todo: enable reading a kubeconfig?
    protected KubernetesClient newK8SClient() {
        return new KubernetesClient(new KubernetesClientConfiguration()
                .setToken(ofNullable(configuration.token()).orElseGet(() -> System.getProperty("java.io.tmpdir", "/tmp") + "missing_jdbcsh_" + UUID.randomUUID() + "_jdbcsh"))
                .setCertificates(decodeIfNeeded(configuration.certificates()))
                .setPrivateKey(decodeIfNeeded(configuration.privateKey()))
                .setPrivateKeyCertificate(decodeIfNeeded(configuration.privateKeyCertificate()))
                .setMaster(configuration.api()));
    }

    private String findPod(final KubernetesClient client, final String namespace) {
        return ofNullable(configuration.pod())
                .filter(Predicate.not(String::isBlank))
                .or(() -> ofNullable(configuration.podPrefix())
                        .flatMap(prefix -> findFromPodPrefixAndSelector(client, namespace, prefix, configuration.labelSelectors())))
                .or(() -> ofNullable(configuration.service())
                        .flatMap(prefix -> findFromService(client, namespace, prefix)))
                .orElseThrow(() -> new IllegalArgumentException("No pod set in namespace '" + namespace + "' for connection '" + connectionName + "'"));
    }

    @SuppressWarnings("unchecked")
    private Optional<String> findFromService(final KubernetesClient client,
                                             final String namespace, final String service) {
        try {
            final var services = client.send(
                    HttpRequest.newBuilder()
                            .GET()
                            .header("Accept", "application/json, */*")
                            .uri(URI.create("https://kubernetes.api/api/v1/namespaces/" + namespace + "/services/" + service))
                            .build(),
                    ofByteArray());
            if (services.statusCode() != 200) {
                throw new IllegalStateException("Can't fetch service: '" + services + "'");
            }

            final var json = (Map<String, Object>) jsonMapper.fromBytes(Object.class, services.body());
            final var labels = (Map<String, Object>) ((Map<String, Object>) json.getOrDefault("spec", Map.of())).getOrDefault("selector", Map.of());
            if (labels.isEmpty()) {
                throw new IllegalArgumentException("No selector for service '" + service + "'");
            }

            final var labelSelector = labels.entrySet().stream()
                    .map(it -> it.getKey() + '=' + it.getValue())
                    .collect(joining(","));
            return findFromPodPrefixAndSelector(client, namespace, null, labelSelector);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return empty();
        }
    }

    @SuppressWarnings("unchecked")
    private Optional<String> findFromPodPrefixAndSelector(final KubernetesClient client,
                                                          final String namespace, final String prefix,
                                                          final String labelSelectors) {
        try {
            final var uri = URI.create("https://kubernetes.api/api/v1/namespaces/" + namespace + "/pods?limit=1000" +
                    (labelSelectors == null || labelSelectors.isBlank() ? "" : ("&labelSelector=" + URLEncoder.encode(labelSelectors, UTF_8))));
            final var pods = client.send(
                    HttpRequest.newBuilder()
                            .GET()
                            .header("Accept", "application/json, */*")
                            .uri(uri)
                            .build(),
                    ofByteArray());
            if (pods.statusCode() != 200) {
                throw new IllegalStateException("Can't fetch pods: " + pods);
            }

            final var json = (Map<String, Object>) jsonMapper.fromBytes(Object.class, pods.body());
            return ((List<Map<String, Object>>) json.get("items")).stream()
                    .map(it -> (Map<String, Object>) it.getOrDefault("metadata", Map.of()))
                    .map(it -> String.valueOf(it.getOrDefault("name", "")))
                    .filter(it -> prefix == null || prefix.isBlank() || it.startsWith(prefix))
                    .findFirst();
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return empty();
        }
    }

    private String decodeIfNeeded(final String pem) {
        return pem != null && !pem.contains("---") ?
                new String(Base64.getDecoder().decode(pem)) : pem;
    }

    public record ForwardingContext(KubernetesClient client, LocalProxy proxy) implements AutoCloseable {
        @Override
        public void close() throws Exception {
            if (client != null) {
                client.close();
            }
            if (proxy != null) {
                proxy.close();
            }
        }
    }
}
