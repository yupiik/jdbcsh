package io.yupiik.jdbcsh.configuration;

import io.yupiik.fusion.framework.build.api.configuration.Property;
import io.yupiik.fusion.framework.build.api.json.JsonModel;

import java.util.List;

@JsonModel
public record JDBCConnection(
        @Property(documentation = "Connection name.") String name,
        @Property(documentation = "Kubernetes connection if needed (to use port forward).") KubernetesPortForwardConfiguration k8s,
        @Property(documentation = "JDBC URL. If you configure kubernetes (`k8s`) you can use `$host:$port` to be replaced by the proxy one if not hardcoded.") String url,
        @Property(documentation = "Database username.") String username,
        @Property(documentation = "Database password.") String password,
        @Property(documentation = "Query/statement aliases, enables to bind a name to a SQL statement (useful when long). These are specific for this database.") List<StatementAlias> aliases
) {
}
