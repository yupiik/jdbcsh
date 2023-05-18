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
