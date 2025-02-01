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
package io.yupiik.jdbcsh.build.minisite;

import io.yupiik.fusion.cli.internal.CliCommand;
import io.yupiik.fusion.framework.api.ConfiguringContainer;
import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.FusionListener;
import io.yupiik.fusion.framework.api.container.FusionModule;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.main.Args;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.jdbcsh.io.StdIO;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;

public class Commands implements Runnable {
    private final Path sourceBase;

    public Commands(final Path sourceBase) {
        this.sourceBase = sourceBase;
    }

    @Override
    public void run() {
        try (final var container = ConfiguringContainer.of()
                .register(new BaseBean<Args>(Args.class, DefaultScoped.class, 1000, Map.of()) {
                    @Override
                    public Args create(final RuntimeContainer container, final List<Instance<?>> dependents) {
                        return new Args(List.of());
                    }
                })
                .register(new BaseBean<StdIO>(StdIO.class, DefaultScoped.class, 1000, Map.of()) {
                    @Override
                    public StdIO create(final RuntimeContainer container, final List<Instance<?>> dependents) {
                        return new StdIO(new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream()), new ByteArrayInputStream(new byte[0]));
                    }
                })
                .register(new FusionModule() { // skip CLI since we don't want to launch anything
                    @Override
                    public int priority() { // before others to have filters active
                        return 0;
                    }

                    @Override
                    public BiPredicate<RuntimeContainer, FusionListener<?>> listenerFilter() {
                        return (c, l) -> false;
                    }
                })
                .start();
             final var executor = container.lookups(CliCommand.class, i -> i.stream().map(Instance::instance).toList())) {
            final var doc = "= Commands\n" +
                    ":minisite-index: 300\n" +
                    ":minisite-index-title: Commands\n" +
                    ":minisite-index-description: Command list.\n" +
                    ":minisite-index-icon: terminal\n" +
                    "\n" +
                    executor.instance().stream()
                            .sorted(comparing(CliCommand::name))
                            .map(c -> (CliCommand<Runnable>) c)
                            .map(it -> "== " + it.name() + "\n" +
                                    "\n" +
                                    it.description() + "\n" +
                                    "\n" +
                                    "=== Parameters\n" +
                                    "\n" +
                                    (it.parameters().isEmpty() ?
                                            ("statement".equals(it.name()) && it.parameters().isEmpty() ? "The SQL statement.\n" : "No parameter.\n") :
                                            it.parameters().stream()
                                                    .sorted(comparing(CliCommand.Parameter::cliName))
                                                    .map(p -> "* `" + p.cliName().replace("-.", "--") + "`: " + p.description())
                                                    .collect(joining("\n", "", "\n")) +
                                                    "\n"))
                            .collect(joining("\n\n"));
            // todo: generate one file per command and just an index?
            Files.writeString(Files.createDirectories(sourceBase.resolve("content/generated/commands")).resolve("index.adoc"), doc);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
