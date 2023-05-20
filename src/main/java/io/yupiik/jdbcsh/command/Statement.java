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
import io.yupiik.jdbcsh.command.error.CommandExecutionException;
import io.yupiik.jdbcsh.io.StdIO;
import io.yupiik.jdbcsh.service.CommandExecutor;
import io.yupiik.jdbcsh.service.State;
import io.yupiik.jdbcsh.table.TableFormatter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Instant.now;
import static java.util.stream.Collectors.joining;

@Command(name = "statement", description = "Execute a statement, it is the implicit command and takes the full args as the statement to execute.")
public class Statement implements Runnable {
    private final Conf conf;
    private final CommandExecutor.CommandArgs args;
    private final StdIO io;
    private final State state;

    public Statement(final Conf conf, final CommandExecutor executor, final StdIO io, final State state) {
        this.conf = conf;
        this.args = executor.currentArgs();
        this.io = io;
        this.state = state;
    }

    @Override
    public void run() {
        if (!state.hasConnection()) {
            throw new IllegalStateException("No connection set, ensure to call `set-connection --name $connection_name`.");
        }

        // todo: add tuning like fetch-size etc but args.args() is parsed so need to enhanced the input parsing to not loose the raw query
        final var sql = args.raw().startsWith("statement ") ? args.raw().substring("statement ".length()) : args.raw();
        final var actualSql = state.findByAlias(sql).orElse(sql);

        final List<List<String>> rows;
        final var start = now();
        try (final var connectionHolder = state.connection();
             final var stmt = connectionHolder.connection().createStatement()) {
            if (stmt.execute(actualSql)) {
                try (final var rset = stmt.getResultSet()) {
                    final var metaData = rset.getMetaData();
                    final var columnCount = metaData.getColumnCount();
                    rows = new ArrayList<>();
                    rows.add(IntStream.rangeClosed(1, columnCount)
                            .mapToObj(i -> {
                                try {
                                    return metaData.getColumnName(i);
                                } catch (final SQLException e) {
                                    throw new CommandExecutionException(e);
                                }
                            })
                            .toList());
                    while (rset.next()) {
                        rows.add(IntStream.rangeClosed(1, columnCount)
                                .mapToObj(i -> {
                                    try {
                                        return toString(rset.getObject(i));
                                    } catch (final SQLException e) {
                                        throw new CommandExecutionException(e);
                                    }
                                })
                                .toList());
                    }
                }
            } else {
                io.stdout().println("Statement executed, it didn't return anything.");
                rows = null;
            }
        } catch (final Exception e) {
            throw new CommandExecutionException(e);
        } finally {
            final var end = now();
            io.stdout().println("Statement execution done in " + Duration.between(start, end).toMillis() + "ms");
        }

        if (rows != null) {
            io.stdout().println(new TableFormatter(rows, state.tableOptions()));
        }
    }

    private String toString(final Object object) {
        if (object == null) {
            return "";
        }
        if (object instanceof Reader r) {
            try (final var reader = r instanceof BufferedReader ? (BufferedReader) r : new BufferedReader(r)) {
                return reader.lines().collect(joining("\n"));
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }
        if (object instanceof InputStream s) {
            try (final var in = s) {
                return new String(in.readAllBytes(), UTF_8);
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return String.valueOf(object);
    }

    @RootConfiguration("statement")
    public record Conf() {
    }
}
