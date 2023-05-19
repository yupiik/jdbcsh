package io.yupiik.jdbcsh.service;

import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.jdbcsh.command.error.CommandExecutionException;
import io.yupiik.jdbcsh.configuration.JDBCConnection;
import io.yupiik.jdbcsh.k8s.PortForward;
import io.yupiik.jdbcsh.table.TableFormatter;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static java.util.Optional.ofNullable;

@ApplicationScoped
public class State {
    private final JsonMapper jsonMapper;

    private JDBCConnection connection;
    private TableFormatter.TableOptions tableOptions = new TableFormatter.TableOptions(false, "-");
    private String prompt = "$database> ";

    public State(final JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    // todo: keep track?
    public void setConnection(final JDBCConnection connection) {
        this.connection = connection;
    }

    public boolean hasConnection() {
        return connection != null;
    }

    public CloseableConnection connection() {
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

    public record CloseableConnection(Connection connection, AutoCloseable closeable) implements AutoCloseable {
        @Override
        public void close() throws Exception {
            closeable.close();
        }
    }
}
