package io.yupiik.jdbcsh.command;

import io.yupiik.jdbcsh.test.InMemoryIO;
import io.yupiik.jdbcsh.test.JDBCshTest;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ListConnectionsTest {
    @JDBCshTest(value = "list-connections")
    void execute(final Supplier<InMemoryIO> io) {
        assertEquals("""
                Switched to connection 'test-h2'
                Available connections:
                * test-h2
                                
                """, io.get().stdout());
    }
}
