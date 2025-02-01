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
package io.yupiik.jdbcsh.build.minisite;

import io.yupiik.fusion.json.internal.JsonMapperImpl;
import io.yupiik.fusion.json.pretty.PrettyJsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static java.util.Optional.empty;
import static java.util.stream.Collectors.toMap;

public class Configuration implements Runnable {
    private final Path sourceBase;
    private final Path outputBase;

    public Configuration(final Path sourceBase, final Path outputBase) {
        this.sourceBase = sourceBase;
        this.outputBase = outputBase;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void run() {
        final var schema = sourceBase.getParent().getParent().getParent().resolve("target/classes/META-INF/fusion/json/schemas.json");
        final var out = sourceBase.resolve("content/_partials/generated/configuration.adoc");
        try (final var jsonMapper = new JsonMapperImpl(List.of(), k -> empty())) {
            Files.createDirectories(out.getParent());
            final var schemaContent = Files.readString(schema);
            final var schemaInstance = jsonMapper.fromString(Object.class, schemaContent);
            final var schemas = (Map<String, Object>) ((Map<String, Object>) schemaInstance).get("schemas");
            final var resolvedSchema = new PrettyJsonMapper(jsonMapper)
                    .toString(resolveRefs(schemas, schemas.get(io.yupiik.jdbcsh.configuration.Configuration.class.getName())));

            Files.writeString( // doc usage
                    out.getParent().resolve("configuration.schema.json"),
                    resolvedSchema);
            Files.writeString( // ref in json files
                    Files.createDirectories(outputBase).resolve("configuration.schema.json"),
                    resolvedSchema);

            // clean up .adoc which is too technical out of the box
            final var adoc = sourceBase.resolve("content/_partials/generated/configuration.json.adoc");
            Files.writeString(adoc, Files.readString(adoc)
                    // drop qualified name from titles
                    .replace("= io.yupiik.jdbcsh.configuration.", "= ")
                    .replace("<<io.yupiik.jdbcsh.configuration.", "<<conf_")
                    .replace("[#io.yupiik.jdbcsh.configuration.", "[#conf_"));
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Object resolveRefs(final Map<String, Object> schemas, final Object schema) {
        if (!(schema instanceof Map<?, ?> map)) {
            return schema;
        }

        final var ref = map.get("$ref");
        if (ref instanceof String s && s.startsWith("#/schemas/")) {
            final var resolved = schemas.get(s.substring("#/schemas/".length()));
            if (resolved != null) {
                final Map<String, Object> out = new TreeMap<>((Map<String, ?>) map);
                out.remove("$ref");
                out.putAll((Map<String, Object>) resolveRefs(schemas, resolved));
                return out;
            }
        }

        final var items = map.get("items");
        if (items instanceof Map<?, ?> itemsAsMap) {
            final var resolved = resolveRefs(schemas, itemsAsMap);
            if (resolved != itemsAsMap) {
                final Map<String, Object> out = new TreeMap<>((Map<String, ?>) map);
                out.put("items", resolved);
                return out;
            }
        }

        final var properties = map.get("properties");
        if (properties instanceof Map<?, ?> propertiesAsMap) {
            final Map<String, Object> out = (Map<String, Object>) new TreeMap<>(map);
            final Map<String, Object> newProps = ((Map<String, Object>) propertiesAsMap)
                    .entrySet().stream()
                    .collect(toMap(Map.Entry::getKey, e -> resolveRefs(schemas, e.getValue()), (a, b) -> a, TreeMap::new));
            out.put("properties", newProps);
            return out;
        }

        return schema;
    }
}
