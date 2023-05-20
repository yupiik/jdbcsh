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

import io.yupiik.fusion.framework.build.api.cli.Command;
import io.yupiik.fusion.framework.build.api.configuration.RootConfiguration;
import io.yupiik.jdbcsh.io.StdIO;
import io.yupiik.jdbcsh.service.ConnectionRegistry;

import static java.util.stream.Collectors.joining;

@Command(name = "list-connections", description = "List available connections (loaded from the configuration).")
public class ListConnections implements Runnable {
    private final Conf conf;
    private final StdIO io;
    private final ConnectionRegistry registry;

    public ListConnections(final Conf conf, final ConnectionRegistry registry, final StdIO io) {
        this.conf = conf;
        this.registry = registry;
        this.io = io;
    }

    @Override
    public void run() {
        io.stdout().println("Available connections:\n" + registry.getConnections().keySet().stream()
                .sorted()
                .map(it -> "* " + it)
                .collect(joining("\n", "", "\n")));
    }

    @RootConfiguration("-")
    public record Conf() {
    }
}
