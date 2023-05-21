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
package io.yupiik.jdbcsh.command;

import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.fusion.framework.build.api.cli.Command;
import io.yupiik.fusion.framework.build.api.configuration.Property;
import io.yupiik.fusion.framework.build.api.configuration.RootConfiguration;
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.jdbcsh.command.error.CommandExecutionException;
import io.yupiik.jdbcsh.configuration.Configuration;
import io.yupiik.jdbcsh.configuration.JDBCConnection;
import io.yupiik.jdbcsh.configuration.KubernetesPortForwardConfiguration;
import io.yupiik.jdbcsh.k8s.yaml.LightYamlParser;
import io.yupiik.jdbcsh.service.CommandExecutor;
import io.yupiik.jdbcsh.service.ConnectionRegistry;
import io.yupiik.jdbcsh.service.State;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;

@DefaultScoped
@Command(name = "load-rc", description = "Load a rc configuration file (json format).")
public class LoadRc implements Runnable {
    private final Conf conf;
    private final ConnectionRegistry registry;
    private final JsonMapper jsonMapper;
    private final CommandExecutor executor;
    private final State state;

    public LoadRc(final Conf conf, final ConnectionRegistry registry, final JsonMapper jsonMapper,
                  final CommandExecutor executor, final State state) {
        this.conf = conf;
        this.registry = registry;
        this.jsonMapper = jsonMapper;
        this.executor = executor;
        this.state = state;
    }

    @Override
    public void run() {
        doLoad(Path.of(requireNonNull(conf.path(), "no --path set")));
    }

    public void doLoad(final Path rc) {
        try {
            final var conf = jsonMapper.fromString(Configuration.class, Files.readString(rc));
            if (conf.connections() != null) {
                final var counter = new AtomicInteger();
                registry.getConnections().putAll(conf.connections().stream()
                        .collect(toMap(
                                it -> ofNullable(it.name())
                                        .orElseGet(() -> "connection-" + counter.incrementAndGet()),
                                this::processConnection)));
            }
            if (conf.initCommands() != null) {
                conf.initCommands().forEach(executor::execute);
            }
            if (conf.prompt() != null) {
                state.setPrompt(conf.prompt());
            }
            if (conf.aliases() != null) {
                state.setGlobalAliases(conf.aliases());
            }
        } catch (final IOException ioe) {
            throw new CommandExecutionException(ioe);
        }
    }

    private JDBCConnection processConnection(final JDBCConnection connection) {
        if (connection.k8s() != null && connection.k8s().kubeconfig() != null) {
            final var conf = Path.of(connection.k8s().kubeconfig());
            if (!Files.exists(conf)) {
                Logger.getLogger(getClass().getName()).warning(() -> "'" + connection.k8s().kubeconfig() + "' not found, skipping.");
                return connection;
            }

            return new JDBCConnection(
                    connection.name(),
                    fillFromKubeConfig(conf, connection.k8s()),
                    connection.url(),
                    connection.username(),
                    connection.password(),
                    connection.aliases(),
                    connection.persistent());
        }
        return connection;
    }

    @SuppressWarnings("unchecked")
    private KubernetesPortForwardConfiguration fillFromKubeConfig(final Path kubeconfig, final KubernetesPortForwardConfiguration origin) {
        final var conf = loadKubeConfig(kubeconfig);
        final var currentContext = conf.get("current-context");
        if (!(currentContext instanceof String currentCtx)) {
            Logger.getLogger(getClass().getName()).warning(() -> "No current-context in '" + kubeconfig + "' skipping autoconfiguration of k8s connection.");
            return origin;
        }

        final var contexts = conf.get("contexts");
        if (!(contexts instanceof List<?> ctxs)) {
            Logger.getLogger(getClass().getName()).warning(() -> "No contexts in '" + kubeconfig + "' skipping autoconfiguration of k8s connection.");
            return origin;
        }

        final var ctx = ctxs.stream()
                .map(it -> (Map<String, Object>) it)
                .filter(it -> it.getOrDefault("name", "").equals(currentCtx))
                .findFirst()
                .map(it -> (Map<String, Object>) it.get("context"))
                .orElse(null);
        if (ctx == null) {
            Logger.getLogger(getClass().getName()).warning(() -> "No context '" + currentCtx + "' in '" + kubeconfig + "' skipping autoconfiguration of k8s connection.");
            return origin;
        }

        final var namespace = ofNullable(origin.namespace())
                .or(() -> ofNullable(ctx.get("namespace")).map(Object::toString))
                .orElse("default");
        final var selectedCluster = findIn(ctx, "cluster", conf, "clusters");
        final var selectedUser = findIn(ctx, "user", conf, "users");

        final var api = selectedCluster
                .map(it -> it.get("server"))
                .map(Object::toString)
                .orElse(origin.api());
        final var certificates = selectedCluster
                .map(it -> {
                    try {
                        if (it.get("certificate-authority") instanceof String filePath) {
                            return Files.readString(Path.of(filePath));
                        }
                        if (it.get("certificate-authority-data") instanceof String data) {
                            return new String(Base64.getDecoder().decode(data), UTF_8);
                        }
                    } catch (final IOException ioe) {
                        throw new IllegalStateException(ioe);
                    }
                    return null;
                })
                .orElse(origin.certificates());
        final var skipTls = selectedCluster
                .map(it -> it.get("insecure-skip-tls-verify") instanceof String skip && Boolean.parseBoolean(skip))
                .orElse(origin.skipTls());
        final var token = selectedUser
                .map(it -> {
                    try {
                        if (it.get("token") instanceof String data) {
                            return data;
                        }
                        if (it.get("tokenFile") instanceof String path) {
                            return Files.readString(Path.of(path));
                        }
                    } catch (final IOException ioe) {
                        throw new IllegalStateException(ioe);
                    }
                    return null;
                })
                .map(Object::toString)
                .orElse(origin.token());
        final var privateKey = selectedUser
                .map(it -> {
                    try {
                        if (it.get("client-key") instanceof String filePath) {
                            return Files.readString(Path.of(filePath));
                        }
                        if (it.get("client-key-data") instanceof String data) {
                            return new String(Base64.getDecoder().decode(data), UTF_8);
                        }
                    } catch (final IOException ioe) {
                        throw new IllegalStateException(ioe);
                    }
                    return null;
                })
                .map(Object::toString)
                .orElse(origin.privateKey());
        final var privateKeyCertificate = selectedUser
                .map(it -> {
                    try {
                        if (it.get("client-certificate") instanceof String filePath) {
                            return Files.readString(Path.of(filePath));
                        }
                        if (it.get("client-certificate-data") instanceof String data) {
                            return new String(Base64.getDecoder().decode(data), UTF_8);
                        }
                    } catch (final IOException ioe) {
                        throw new IllegalStateException(ioe);
                    }
                    return null;
                })
                .map(Object::toString)
                .orElse(origin.privateKeyCertificate());

        return new KubernetesPortForwardConfiguration(
                origin.kubeconfig(),
                token,
                privateKey,
                privateKeyCertificate,
                certificates,
                skipTls,
                api,
                origin.containerPort(),
                origin.localAddress(),
                origin.localPort(),
                origin.pod(),
                origin.podPrefix(),
                origin.service(),
                namespace,
                origin.labelSelectors());
    }

    @SuppressWarnings("unchecked")
    private Optional<Map<String, Object>> findIn(final Map<String, Object> ctx, final String attribute,
                                                 final Map<String, Object> conf, final String list) {
        return ofNullable(ctx.get(attribute))
                .map(Object::toString)
                .flatMap(cluster -> ofNullable(conf.get(list))
                        .map(it -> (List<Map<String, Object>>) it)
                        .flatMap(it -> it.stream()
                                .filter(c -> c.getOrDefault("name", "").equals(cluster))
                                .findFirst()))
                .map(it -> (Map<String, Object>) it.get(attribute));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadKubeConfig(final Path kubeconfig) {
        try (final var reader = Files.newBufferedReader(kubeconfig)) {
            if (kubeconfig.getFileName().toString().endsWith(".json")) {
                return (Map<String, Object>) jsonMapper.read(Object.class, reader);
            }
            return (Map<String, Object>) new LightYamlParser().parse(reader);
        } catch (final IOException e) {
            throw new IllegalArgumentException("Can't parse kubeconfig: '" + kubeconfig + "'", e);
        }
    }

    @RootConfiguration("-")
    public record Conf(@Property(documentation = "Rc file path.") String path) {
    }
}
