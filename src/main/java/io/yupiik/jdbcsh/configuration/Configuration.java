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
package io.yupiik.jdbcsh.configuration;

import io.yupiik.fusion.framework.build.api.configuration.Property;
import io.yupiik.fusion.framework.build.api.json.JsonModel;

import java.util.List;

@JsonModel
public record Configuration(
        @Property(documentation = "List of defined connections.") List<JDBCConnection> connections,
        @Property(documentation = "Init commands (can be used to `set-connection` automatically.") List<String> initCommands,
        @Property(documentation = "Prompt for the interactive mode.") String prompt,
        @Property(documentation = "Query/statement aliases, enables to bind a name to a SQL statement (useful when long). These are global for all databases.") List<StatementAlias> aliases) {
}
