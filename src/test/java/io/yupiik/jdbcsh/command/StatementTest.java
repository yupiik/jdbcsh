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
