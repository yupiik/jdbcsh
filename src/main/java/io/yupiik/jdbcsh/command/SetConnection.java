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
