package io.yupiik.jdbcsh.launcher;

import io.yupiik.fusion.framework.api.lifecycle.Start;
import io.yupiik.fusion.framework.api.main.Args;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.fusion.framework.build.api.event.OnEvent;
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.jdbcsh.configuration.Configuration;
import io.yupiik.jdbcsh.service.CommandExecutor;
import io.yupiik.jdbcsh.service.ConnectionRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

@DefaultScoped
public class CLI {
    private final CommandExecutor executor;

    public CLI(final CommandExecutor executor) {
        this.executor = executor;
    }

    public void onStart(@OnEvent final Start start,
                        final Args args,
                        final JsonMapper jsonMapper,
                        final ConnectionRegistry registry) {
        boolean skipDefaultRc = false;
        if (args.args() != null) {
            skipDefaultRc = args.args().contains("-sdrc");

            final int conf = args.args().indexOf("-rc");
            if (conf >= 0) {
                initRC(Path.of(args.args().get(conf + 1)), jsonMapper, registry);
            }

            final int commands = args.args().indexOf("-c");
            if (commands >= 0) {
                if (!skipDefaultRc) {
                    initDefaultRC(jsonMapper, registry);
                    skipDefaultRc = true; // already done
                }
                execute(args.args().get(commands + 1));
            }

            final int noInteractive = args.args().indexOf("-ni");
            if (noInteractive >= 0) {
                return;
            }
        }
        if (!skipDefaultRc) {
            initDefaultRC(jsonMapper, registry);
        }

        startInteractive();
    }

    private void startInteractive() {
        // todo: while + prompt + try/catch(CommandExecutionException) to not quit on error
        System.out.println("TBD");
    }

    private void initDefaultRC(final JsonMapper jsonMapper, final ConnectionRegistry registry) {
        initRC(Path.of(System.getProperty("user.home", ".")).resolve(".jdbcshrc"), jsonMapper, registry);
    }

    private void execute(final String commands) {
        final var location = Path.of(commands);
        if (Files.exists(location)) {
            try {
                executeCommands(Files.readString(location));
            } catch (final IOException e) {
                throw new IllegalArgumentException("Command file not found: '" + commands + "'", e);
            }
        } else {
            executeCommands(commands);
        }
    }

    private void executeCommands(final String commands) {
        Stream.of(commands.split("\n"))
                .map(String::strip)
                .filter(Predicate.not(String::isBlank))
                .filter(Predicate.not(it -> it.startsWith("#")))
                .forEach(executor::execute);
    }

    private void initRC(final Path rc, final JsonMapper jsonMapper, final ConnectionRegistry registry) {
        if (Files.exists(rc)) {
            loadFromJsonConf(jsonMapper, registry, rc);
        }
    }

    private void loadFromJsonConf(final JsonMapper jsonMapper, final ConnectionRegistry registry, final Path rc) {
        try {
            final var conf = jsonMapper.fromString(Configuration.class, Files.readString(rc));
            if (conf.connections() != null) {
                final var counter = new AtomicInteger();
                registry.getConnections().putAll(conf.connections().stream()
                        .collect(toMap(
                                it -> ofNullable(it.name())
                                        .orElseGet(() -> "connection-" + counter.incrementAndGet()),
                                identity())));
            }
            if (conf.initCommands() != null) {
                conf.initCommands().forEach(executor::execute);
            }
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
