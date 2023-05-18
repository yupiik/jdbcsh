package io.yupiik.jdbcsh.command;

import io.yupiik.fusion.framework.build.api.cli.Command;
import io.yupiik.fusion.framework.build.api.configuration.Property;
import io.yupiik.fusion.framework.build.api.configuration.RootConfiguration;
import io.yupiik.jdbcsh.io.StdIO;
import io.yupiik.jdbcsh.service.State;
import io.yupiik.jdbcsh.table.TableFormatter;

@Command(name = "set-table-options", description = "Switch table options.")
public class SetTableOptions implements Runnable {
    private final Conf conf;
    private final StdIO io;
    private final State state;

    public SetTableOptions(final Conf conf, final StdIO io, final State state) {
        this.conf = conf;
        this.io = io;
        this.state = state;
    }

    @Override
    public void run() {
        state.setTableOptions(new TableFormatter.TableOptions(conf.transpose(), conf.lineSeparatorChar()));
        io.stdout().println("Switched table options.");
    }

    @RootConfiguration("-")
    public record Conf(
            @Property(documentation = "Tables should be transposed, ie the headers are on the first column.", defaultValue = "false") boolean transpose,
            @Property(documentation = "Header character to separator header line from data lines and create border lines. It will also show record by record (blocks).", defaultValue = "\"-\"") String lineSeparatorChar) {
    }
}
