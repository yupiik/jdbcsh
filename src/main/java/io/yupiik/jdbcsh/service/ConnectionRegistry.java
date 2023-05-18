package io.yupiik.jdbcsh.service;

import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.jdbcsh.configuration.JDBCConnection;

import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class ConnectionRegistry {
    private final Map<String, JDBCConnection> connections = new HashMap<>();

    public Map<String, JDBCConnection> getConnections() {
        return connections;
    }
}
