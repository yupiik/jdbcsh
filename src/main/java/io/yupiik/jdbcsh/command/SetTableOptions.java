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
