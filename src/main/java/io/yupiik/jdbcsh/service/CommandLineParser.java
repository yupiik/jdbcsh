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
package io.yupiik.jdbcsh.service;

import io.yupiik.fusion.framework.api.scope.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

@ApplicationScoped
public class CommandLineParser {
    public List<String> parse(final String line) { // from ant
        final int normal = 0;
        final int inQuote = 1;
        final int inDoubleQuote = 2;
        int state = normal;
        final var tok = new StringTokenizer(line, "\"' ", true);
        final var result = new ArrayList<String>();
        final var current = new StringBuilder();
        boolean lastTokenHasBeenQuoted = false;
        while (tok.hasMoreTokens()) {
            String nextTok = tok.nextToken();
            switch (state) {
                case inQuote -> {
                    if ("'".equals(nextTok)) {
                        lastTokenHasBeenQuoted = true;
                        state = normal;
                    } else {
                        current.append(nextTok);
                    }
                }
                case inDoubleQuote -> {
                    if ("\"".equals(nextTok)) {
                        lastTokenHasBeenQuoted = true;
                        state = normal;
                    } else {
                        current.append(nextTok);
                    }
                }
                default -> {
                    if ("'".equals(nextTok)) {
                        state = inQuote;
                    } else if ("\"".equals(nextTok)) {
                        state = inDoubleQuote;
                    } else if (" ".equals(nextTok)) {
                        if (lastTokenHasBeenQuoted || !current.isEmpty()) {
                            result.add(current.toString());
                            current.setLength(0);
                        }
                    } else {
                        current.append(nextTok);
                    }
                    lastTokenHasBeenQuoted = false;
                }
            }
        }

        if (lastTokenHasBeenQuoted || !current.isEmpty()) {
            result.add(current.toString());
        }
        if (state == inQuote || state == inDoubleQuote) {
            throw new IllegalStateException("unbalanced quotes in '" + line + "'");
        }

        return result;
    }
}
