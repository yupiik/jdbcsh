package io.yupiik.jdbcsh.command;

import io.yupiik.jdbcsh.test.InMemoryIO;
import io.yupiik.jdbcsh.test.JDBCshTest;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatementTest {
    @JDBCshTest(value = "select id, name from test order by name desc", createDatabase = true)
    void execute(final Supplier<InMemoryIO> io) {
        assertEquals("""
                Switched to connection 'test-h2'
                Statement execution done in Xms
                ---------------
                | ID   | NAME |
                ---------------
                | 0002 | efgh |
                | 0001 | abcd |
                ---------------
                                
                """, io.get().stdout());
    }

    @JDBCshTest(value = "select id, name from test order by name desc", createDatabase = true, rc = "src/test/resources/testrc.transpose.json")
    void executeTransposed(final Supplier<InMemoryIO> io) {
        assertEquals("""
                Switched to connection 'test-h2'
                Switched table options.
                Statement execution done in Xms
                ---------------
                |   ID | 0002 |
                | NAME | efgh |
                ---------------
                                
                ---------------
                |   ID | 0001 |
                | NAME | abcd |
                ---------------
                                
                """, io.get().stdout());
    }
}
