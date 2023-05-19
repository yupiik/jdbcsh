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
