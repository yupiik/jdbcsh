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

@JsonModel
public record KubernetesPortForwardConfiguration(
        // todo: add kubeconfig context/user/cluster selection?
        @Property(documentation = "Kubeconfig to use to fill kubernetes configuration automatically (certificates, token or certificate authentication). Note that the parser is not a generic Yaml parser but a light one supporting only inline values (not multiline strings). The logic is to parse the file as a plain JSON file if extension is JSON, else use the light YAML parser. As of now it uses default cluster/context/user.") String kubeconfig,
        @Property(documentation = "Token (if not refreshed and not using a certificate as authentication.). Ignored if `kubeconfig` is used.") String token,
        @Property(documentation = "If not using a token the SSL private key for cluster authentication. Ignored if `kubeconfig` is used.") String privateKey,
        @Property(documentation = "If not using a token the SSL private key certificate for cluster authentication. Ignored if `kubeconfig` is used.") String privateKeyCertificate,
        @Property(documentation = "SSL certificates (not for authentication there, can be a chain). Ignored if `kubeconfig` is used.") String certificates,
        @Property(documentation = "Should TLS error be ignored. Ignored if `kubeconfig` is used.") boolean skipTls,
        @Property(documentation = "Master Kubernetes API URL. Ignored if `kubeconfig` is used.") String api,
        @Property(documentation = "Port to target on the pod/service.") int containerPort,
        @Property(documentation = "Local address/interface to use for the proxy, only set it if you have multiple interfaces and you know what it means.", defaultValue = "\"localhost\"") String localAddress,
        @Property(documentation = "Port to target locally in the 'proxy' service (if `0`) it will be random.", defaultValue = "0") int localPort,
        @Property(documentation = "Pod name (if not discovered, for databases it is often `$statefulset-0`).") String pod,
        @Property(documentation = "Pod prefix, if `pod` is not set they will be queried using this prefix in the configured namespace and the first one matching will be taken.") String podPrefix,
        @Property(documentation = "Service name, if `pod` is not set and `podPrefix` is not set too, services will be queried using this prefix in the configured namespace and the first matching bound port will be taken (note: it must use a label selector).") String service,
        @Property(documentation = "Namespace to use. If not set and `kubeconfig` is set it can be autofilled.", defaultValue = "\"default\"") String namespace,
        @Property(documentation = "Label selector filter for pod prefix queries (not encoded).") String labelSelectors) {
}
