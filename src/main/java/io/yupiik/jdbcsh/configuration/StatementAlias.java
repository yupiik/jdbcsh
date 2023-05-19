package io.yupiik.jdbcsh.configuration;

import io.yupiik.fusion.framework.build.api.json.JsonModel;

@JsonModel
public record StatementAlias(String name, String sql) {
}
