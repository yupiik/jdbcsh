package io.yupiik.jdbcsh.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Test
@Target(METHOD)
@Retention(RUNTIME)
@ExtendWith(Runner.class)
public @interface JDBCshTest {
    String value();

    boolean createDatabase() default false;

    String rc() default "src/test/resources/testrc.json";
}
