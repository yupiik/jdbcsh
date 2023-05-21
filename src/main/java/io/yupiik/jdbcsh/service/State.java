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
package io.yupiik.jdbcsh.service;

import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.framework.build.api.lifecycle.Destroy;
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.jdbcsh.command.error.CommandExecutionException;
import io.yupiik.jdbcsh.configuration.JDBCConnection;
import io.yupiik.jdbcsh.configuration.StatementAlias;
import io.yupiik.jdbcsh.k8s.PortForward;
import io.yupiik.jdbcsh.table.TableFormatter;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

import static java.util.Optional.ofNullable;
import static java.util.logging.Level.WARNING;

@ApplicationScoped
public class State {
    private final JsonMapper jsonMapper;

    private JDBCConnection connection;
    private TableFormatter.TableOptions tableOptions = new TableFormatter.TableOptions(false, "-");
    private String prompt = "$database> ";
    private List<StatementAlias> globalAliases = List.of();
    private CloseableConnection lastConnection;

    public State(final JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Destroy
    protected void destroy() {
        if (lastConnection != null) {
            try {
                lastConnection.closeable().close();
            } catch (final Exception e) {
                Logger.getLogger(getClass().getName()).log(WARNING, e, () -> "Can't close last connection properly: " + e.getMessage());
            }
        }
    }

    public void setConnection(final JDBCConnection connection) {
        this.connection = connection;
        if (this.lastConnection != null) {
            try {
                this.lastConnection.closeable().close();
            } catch (final Exception e) {
                Logger.getLogger(getClass().getName()).log(WARNING, e, () -> "Error closing last connection properly: " + e.getMessage());
            } finally {
                this.lastConnection = null;
            }
        }
    }

    public boolean hasConnection() {
        return connection != null;
    }

    public Optional<String> findByAlias(final String sql) {
        return ofNullable(connection)
                .map(JDBCConnection::aliases)
                .flatMap(a -> findByAlias(sql, a))
                .or(() -> findByAlias(sql, globalAliases));
    }

    private Optional<String> findByAlias(final String sql, final List<StatementAlias> a) {
        return a.stream()
                .filter(it -> Objects.equals(it.name(), sql))
                .findFirst()
                .map(StatementAlias::sql);
    }

    public CloseableConnection connection() {
        if (lastConnection != null) {
            return new CloseableConnection(lastConnection.connection(), () -> {
            });
        }

        final var freshConnection = doCreateConnection();
        if (connection.persistent()) {
            lastConnection = freshConnection;
        }
        return freshConnection;
    }

    private CloseableConnection doCreateConnection() {
        if (connection.k8s() != null) {
            final var portForward = new PortForward(
                    connection.k8s(),
                    connection.name(),
                    jsonMapper);
            final var forwarding = portForward.launch();
            try {
                final Connection jdbc;
                try {
                    jdbc = DriverManager.getConnection(
                            connection.url()
                                    .replace("$host", forwarding.proxy().localAddress().getHostName())
                                    .replace("$port", Integer.toString(forwarding.proxy().localAddress().getPort())),
                            connection.username(), connection.password());
                } catch (final IOException e) {
                    try {
                        forwarding.close();
                    } catch (final Exception ex) {
                        e.addSuppressed(ex);
                    }
                    throw new CommandExecutionException(e);
                }
                return new CloseableConnection(jdbc, () -> {
                    CommandExecutionException ex = null;
                    try {
                        jdbc.close();
                    } catch (final SQLException sqle) {
                        ex = new CommandExecutionException(sqle);
                    }
                    try {
                        forwarding.close();
                    } catch (final Exception e) {
                        if (ex == null) {
                            ex = new CommandExecutionException(e);
                        } else {
                            ex.getCause().addSuppressed(e);
                        }
                    }
                    if (ex != null) {
                        throw ex;
                    }
                });
            } catch (final SQLException e) {
                try {
                    forwarding.close();
                } catch (final Exception ex) {
                    e.addSuppressed(ex);
                }
                throw new CommandExecutionException(e);
            }
        }

        // standard local connection
        try {
            final var jdbc = DriverManager.getConnection(connection.url(), connection.username(), connection.password());
            return new CloseableConnection(jdbc, jdbc);
        } catch (final SQLException e) {
            throw new CommandExecutionException(e);
        }
    }

    public void setTableOptions(final TableFormatter.TableOptions tableOptions) {
        this.tableOptions = tableOptions;
    }

    public TableFormatter.TableOptions tableOptions() {
        return tableOptions;
    }

    public String getCurrentPrompt() {
        return prompt
                .replace("$database", connection == null ? "no-database" : ofNullable(connection.name()).orElse("database1"));
    }

    public void setPrompt(final String prompt) {
        this.prompt = prompt;
    }

    public void setGlobalAliases(final List<StatementAlias> aliases) {
        this.globalAliases = aliases;
    }

    public record CloseableConnection(Connection connection, AutoCloseable closeable) implements AutoCloseable {
        @Override
        public void close() throws Exception {
            closeable.close();
        }
    }
}
