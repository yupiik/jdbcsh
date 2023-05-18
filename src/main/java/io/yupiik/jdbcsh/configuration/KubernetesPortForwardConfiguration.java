package io.yupiik.jdbcsh.configuration;

import io.yupiik.fusion.framework.build.api.configuration.Property;
import io.yupiik.fusion.framework.build.api.json.JsonModel;

@JsonModel
public record KubernetesPortForwardConfiguration(
        @Property(documentation = "Token (if not refreshed and not using a certificate as authentication.)") String token,
        @Property(documentation = "If not using a token the SSL private key for cluster authentication.") String privateKey,
        @Property(documentation = "If not using a token the SSL private key certificate for cluster authentication") String privateKeyCertificate,
        @Property(documentation = "SSL certificates (not for authentication there, can be a chain).") String certificates,
        @Property(documentation = "Master Kubernetes API URL.") String api,
        @Property(documentation = "Port to target on the pod/service.") int containerPort,
        @Property(documentation = "Local address/interface to use for the proxy, only set it if you have multiple interfaces and you know what it means.", defaultValue = "\"localhost\"") String localAddress,
        @Property(documentation = "Port to target locally in the 'proxy' service (if `0`) it will be random.", defaultValue = "0") int localPort,
        @Property(documentation = "Pod name (if not discovered, for databases it is often `$statefulset-0`).") String pod,
        @Property(documentation = "Pod prefix, if `pod` is not set they will be queried using this prefix in the configured namespace and the first one matching will be taken.") String podPrefix,
        @Property(documentation = "Service name, if `pod` is not set and `podPrefix` is not set too, services will be queried using this prefix in the configured namespace and the first matching bound port will be taken (note: it must use a label selector).") String service,
        @Property(documentation = "Namespace to use.", defaultValue = "\"default\"") String namespace,
        @Property(documentation = "Label selector filter for pod prefix queries (not encoded).") String labelSelectors
) {
}
