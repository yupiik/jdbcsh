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
package io.yupiik.jdbcsh.service;

import io.yupiik.fusion.cli.CliAwaiter;
import io.yupiik.fusion.cli.internal.CliCommand;
import io.yupiik.fusion.framework.api.configuration.Configuration;
import io.yupiik.fusion.framework.api.main.Args;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;

import java.util.List;
import java.util.Map;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

@ApplicationScoped
public class CommandExecutor {
    private final Map<String, CliCommand<? extends Runnable>> commands;
    private final Configuration configuration;
    private final CommandLineParser parser;
    private CommandArgs currentArgs;

    public CommandExecutor(final List<CliCommand<? extends Runnable>> allCommands, final Configuration configuration, final CommandLineParser parser) {
        this.commands = allCommands == null ? null : allCommands.stream().collect(toMap(CliCommand::name, identity()));
        this.configuration = configuration;
        this.parser = parser;
    }

    public void execute(final String command) {
        int space = command.indexOf(' ');
        if (space < 0) {
            space = command.length();
        }

        final var cmdName = command.substring(0, space);
        final var cmd = commands.get(cmdName);
        final var args = parser.parse(cmd == null && !"help".equalsIgnoreCase(cmdName) ? "statement " + command : command);
        currentArgs = new CommandArgs(command, args); // for now we are not multi-threaded but could be a thread local or scoped instance
        try {
            CliAwaiter.of(new Args(args), configuration, commands).await();
        } finally {
            currentArgs = null;
        }
    }

    public CommandArgs currentArgs() {
        return currentArgs;
    }

    public record CommandArgs(String raw, List<String> args) {
    }
}
