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
package io.yupiik.jdbcsh.launcher;

import io.yupiik.fusion.framework.api.ConfiguringContainer;
import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.main.Args;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.jdbcsh.io.StdIO;

import java.util.List;
import java.util.Map;

public class Launcher {
    public static void main(final String... args) {
        new Launcher().doRun(new StdIO(System.out, System.err, System.in), args);
    }

    public void doRun(final StdIO stdIO, final String... args) {
        try (final var auto = ConfiguringContainer.of()
                .register(new BaseBean<Args>(Args.class, DefaultScoped.class, 1000, Map.of()) {
                    @Override
                    public Args create(final RuntimeContainer container, final List<Instance<?>> dependents) {
                        return new Args(List.of(args));
                    }
                })
                .register(new BaseBean<StdIO>(StdIO.class, DefaultScoped.class, 1000, Map.of()) {
                    @Override
                    public StdIO create(final RuntimeContainer container, final List<Instance<?>> dependents) {
                        return stdIO;
                    }
                })
                .start()) {
            // a startup bean will handle the cli
        }
    }
}
