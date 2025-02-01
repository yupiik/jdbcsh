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
package io.yupiik.jdbcsh.launcher;

import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.lifecycle.Start;
import io.yupiik.fusion.framework.api.main.Args;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.fusion.framework.build.api.event.OnEvent;
import io.yupiik.jdbcsh.command.LoadRc;
import io.yupiik.jdbcsh.command.error.CommandExecutionException;
import io.yupiik.jdbcsh.io.StdIO;
import io.yupiik.jdbcsh.service.CommandExecutor;
import io.yupiik.jdbcsh.service.State;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.function.Predicate;
import java.util.stream.Stream;

@DefaultScoped
public class CLI {
    private final CommandExecutor executor;

    public CLI(final CommandExecutor executor) {
        this.executor = executor;
    }

    public void onStart(@OnEvent final Start start,
                        final Args args,
                        final StdIO stdIO,
                        final State state,
                        final RuntimeContainer container) {
        boolean skipDefaultRc = false;
        if (args.args() != null) {
            skipDefaultRc = args.args().contains("-sdrc");

            final int conf = args.args().indexOf("-rc");
            if (conf >= 0) {
                initRC(Path.of(args.args().get(conf + 1)), container);
            }

            final int commands = args.args().indexOf("-c");
            if (commands >= 0) {
                if (!skipDefaultRc) {
                    initDefaultRC(container);
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
            initDefaultRC(container);
        }

        startInteractive(state, stdIO);
    }

    private void startInteractive(final State state, final StdIO stdIO) {
        // final var console = System.console(); // does not always work in terminals (if not a tty)
        final var scanner = new Scanner(stdIO.stdin()); // don't close! done by caller if needed (jvm most of the time)
        while (true) {
            try {
                final var prompt = state.getCurrentPrompt();
                stdIO.stdout().print(prompt);
                final var command = scanner.nextLine();
                final var stripped = command.strip();
                if ("exit".equalsIgnoreCase(stripped) || "quit".equalsIgnoreCase(stripped)) {
                    return;
                }
                executor.execute(command);
            } catch (final CommandExecutionException e) {
                stdIO.stderr().println("Command execution failed:");
                e.getCause().printStackTrace(stdIO.stderr());
            } catch (final RuntimeException re) {
                stdIO.stderr().println("Command execution failed:");
                re.printStackTrace(stdIO.stderr());
            }
        }
    }

    private void initDefaultRC(final RuntimeContainer container) {
        initRC(Path.of(System.getProperty("user.home", ".")).resolve(".jdbcshrc"), container);
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

    private void initRC(final Path rc, final RuntimeContainer container) {
        if (Files.exists(rc)) {
            loadFromJsonConf(container, rc);
        }
    }

    private void loadFromJsonConf(final RuntimeContainer container, final Path rc) {
        try (final var instance = container.lookup(LoadRc.class)) {
            instance.instance().doLoad(rc);
        }
    }
}
