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
package io.yupiik.jdbcsh.test;

import io.yupiik.fusion.framework.api.ConfiguringContainer;
import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.main.Args;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.fusion.testing.module.TestingModule;
import io.yupiik.jdbcsh.io.StdIO;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.platform.commons.util.AnnotationUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.ParameterizedType;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

public class Runner implements BeforeEachCallback, ParameterResolver {
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(Runner.class);

    @Override
    public void beforeEach(final ExtensionContext extensionContext) throws Exception {
        AnnotationUtils.findAnnotation(extensionContext.getElement(), JDBCshTest.class)
                .ifPresent(conf -> {
                    final var out = new ByteArrayOutputStream();
                    final var err = new ByteArrayOutputStream();
                    try (final var psOut = new PrintStream(out);
                         final var psErr = new PrintStream(err);
                         final var db = conf.createDatabase() ? newDatabase() : new AutoCloseable() {
                             @Override
                             public void close() {
                                 // no-op
                             }
                         }) {
                        try (final var runner = ConfiguringContainer.of()
                                .register(new TestingModule()) // just by convenience
                                .register(new BaseBean<Args>(Args.class, DefaultScoped.class, 1000, Map.of()) {
                                    @Override
                                    public Args create(final RuntimeContainer container, final List<Instance<?>> dependents) {
                                        return new Args(Stream.concat(
                                                        // load test config and skip interactive mode by default
                                                        Stream.of("-ni", "-sdrc", "-rc", conf.rc(), "-c"),
                                                        Stream.of(conf.value()))
                                                .toList());
                                    }
                                })
                                .register(new BaseBean<StdIO>(StdIO.class, DefaultScoped.class, 1000, Map.of()) {
                                    @Override
                                    public StdIO create(final RuntimeContainer container, final List<Instance<?>> dependents) {
                                        return new StdIO(psOut, psErr, new ByteArrayInputStream(new byte[0]));
                                    }
                                })
                                .start()) {
                        }
                    } catch (final Exception e) {
                        throw new IllegalStateException(e);
                    }
                    extensionContext.getStore(NAMESPACE).put(
                            InMemoryIO.class,
                            new InMemoryIO(
                                    out.toString(StandardCharsets.UTF_8).replaceFirst(" \\d+ms", " Xms"),
                                    err.toString(StandardCharsets.UTF_8)));
                });
    }

    private AutoCloseable newDatabase() {
        try {
            final var connection = DriverManager.getConnection("jdbc:h2:mem:test", "sa", "");
            try (final var stmt = connection.createStatement()) {
                stmt.execute("create table test(id varchar(4), name varchar(16))");
                stmt.execute("insert into test(id, name) values('0001', 'abcd')");
                stmt.execute("insert into test(id, name) values('0002', 'efgh')");
            }
            if (!connection.getAutoCommit()) {
                connection.commit();
            }
            return connection;
        } catch (final SQLException e) {
            return fail(e);
        }
    }

    @Override
    public boolean supportsParameter(final ParameterContext parameterContext, final ExtensionContext extensionContext) throws ParameterResolutionException {
        final var parameterizedType = parameterContext.getParameter().getParameterizedType();
        return parameterizedType instanceof ParameterizedType pt &&
                Supplier.class == pt.getRawType() &&
                pt.getActualTypeArguments().length == 1 &&
                InMemoryIO.class == pt.getActualTypeArguments()[0];
    }

    @Override
    public Object resolveParameter(final ParameterContext parameterContext, final ExtensionContext extensionContext) throws ParameterResolutionException {
        return new Supplier<InMemoryIO>() {
            private InMemoryIO io;

            @Override
            public InMemoryIO get() {
                if (io == null) {
                    io = extensionContext.getStore(NAMESPACE).get(InMemoryIO.class, InMemoryIO.class);
                }
                return io;
            }
        };
    }
}
