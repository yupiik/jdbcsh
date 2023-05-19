package io.yupiik.jdbcsh.configuration;

import io.yupiik.fusion.framework.build.api.configuration.Property;
import io.yupiik.fusion.framework.build.api.json.JsonModel;

import java.util.List;

@JsonModel
public record Configuration(
        @Property(documentation = "List of defined connections.") List<JDBCConnection> connections,
        @Property(documentation = "Init commands (can be used to `set-connection` automatically.") List<String> initCommands,
        @Property(documentation = "Prompt for the interactive mode.") String prompt) {
}
