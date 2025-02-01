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
package io.yupiik.jdbcsh.command;

import io.yupiik.fusion.framework.build.api.cli.Command;
import io.yupiik.fusion.framework.build.api.configuration.Property;
import io.yupiik.fusion.framework.build.api.configuration.RootConfiguration;
import io.yupiik.jdbcsh.command.error.CommandExecutionException;
import io.yupiik.jdbcsh.configuration.JDBCConnection;
import io.yupiik.jdbcsh.io.StdIO;
import io.yupiik.jdbcsh.service.ConnectionRegistry;
import io.yupiik.jdbcsh.service.State;

@Command(name = "set-connection", description = "Execute a statement, it is the implicit command, using `none` name will unset the current connection.")
public class SetConnection implements Runnable {
    private final Conf conf;
    private final StdIO io;
    private final State state;
    private final ConnectionRegistry registry;

    public SetConnection(final Conf conf, final ConnectionRegistry registry, final StdIO io, final State state) {
        this.conf = conf;
        this.registry = registry;
        this.io = io;
        this.state = state;
    }

    @Override
    public void run() {
        state.setConnection("none".equals(conf.name()) ? null : requireNonNull(registry.getConnections().get(conf.name())));
        io.stdout().println("Switched to connection '" + conf.name() + "'");
    }

    private JDBCConnection requireNonNull(final JDBCConnection connection) {
        if (connection == null) {
            throw new CommandExecutionException(new IllegalStateException("No connection '" + conf.name() + "' found."));
        }
        return connection;
    }

    @RootConfiguration("-")
    public record Conf(@Property(documentation = "Name of the connection to use.") String name) {
    }
}
