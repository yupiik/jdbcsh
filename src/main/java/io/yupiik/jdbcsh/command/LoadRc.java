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
import io.yupiik.jdbcsh.service.CommandExecutor;
import io.yupiik.jdbcsh.service.ConnectionRegistry;
import io.yupiik.jdbcsh.service.State;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.function.Function.identity;
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
                        .peek(it -> {
                            if (it.driver() != null) {
                                try {
                                    Thread.currentThread().getContextClassLoader().loadClass(it.driver().strip());
                                } catch (final ClassNotFoundException e) {
                                    Logger.getLogger(getClass().getName()).warning(() -> "Can't load driver '" + it.driver() + "'");
                                }
                            }
                        })
                        .collect(toMap(
                                it -> ofNullable(it.name())
                                        .orElseGet(() -> "connection-" + counter.incrementAndGet()),
                                identity())));
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

    @RootConfiguration("-")
    public record Conf(@Property(documentation = "Rc file path.") String path) {
    }
}
