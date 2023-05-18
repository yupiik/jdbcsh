package io.yupiik.jdbcsh.table;

import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.joining;

public class TableFormatter {
    private final List<List<String>> rows;
    private final TableOptions options;

    public TableFormatter(final List<List<String>> rows, final TableOptions options) {
        this.rows = rows;
        this.options = options;
    }

    @Override
    public String toString() {
        if (rows.isEmpty()) {
            return "";
        }
        if (options.transpose()) {
            final var headers = rows.get(0);
            final int maxHeaderWidth = (int) headers.stream().mapToLong(String::length).max().orElse(0);
            return rows.stream()
                    .skip(1)
                    .map(data -> {
                        final int maxDataWidth = (int) data.stream().mapToLong(String::length).max().orElse(0);
                        final var lineSeparator = options.lineSeparator().repeat(maxDataWidth + maxHeaderWidth + 7 /*column separators*/) + '\n';
                        final var headerIt = headers.iterator();
                        final var dataIt = data.iterator();
                        return lineSeparator +
                                StreamSupport.stream(Spliterators.spliteratorUnknownSize(new Iterator<String>() {
                                            @Override
                                            public boolean hasNext() {
                                                return headerIt.hasNext();
                                            }

                                            @Override
                                            public String next() {
                                                final var name = headerIt.next();
                                                final var content = dataIt.next();
                                                return "| " + (name.length() == maxHeaderWidth ? "" : " ".repeat(maxHeaderWidth - name.length())) + name +
                                                        " | " + (content.length() == maxDataWidth ? "" : " ".repeat(maxDataWidth - content.length())) + content +
                                                        " |";
                                            }
                                        }, Spliterator.IMMUTABLE), false)
                                        .collect(joining("\n", "", "\n")) +
                                lineSeparator;
                    })
                    .collect(joining("\n"));
        }

        // standard tables (but poorly readable)
        final var maxWidthPerColumn = maxWidths(rows);
        final var lineSeparator = options.lineSeparator().repeat(
                (int) maxWidthPerColumn.stream().mapToLong(i -> i).sum() +
                        /*now add separators*/ 4 + (maxWidthPerColumn.size() - 1) * 3);
        if (!"".equals(options.lineSeparator()) && !rows.isEmpty()) {
            return Stream.concat(Stream.concat(
                                    Stream.of(lineSeparator, formatLine(rows.get(0), maxWidthPerColumn), lineSeparator),
                                    rows.stream().skip(1).map(row -> formatLine(row, maxWidthPerColumn))),
                            Stream.of(lineSeparator + '\n'))
                    .collect(joining("\n"));
        }
        return lineSeparator + '\n' + rows.stream().map(row -> formatLine(row, maxWidthPerColumn)).collect(joining("\n")) + lineSeparator + '\n';
    }

    private List<Integer> maxWidths(final List<List<String>> rows) {
        return rows.stream()
                .map(it -> it.stream().map(String::length).toList())
                .reduce(null, (a, b) -> {
                    if (a == null) {
                        return b;
                    }
                    final var it1 = a.iterator();
                    final var it2 = b.iterator();
                    return StreamSupport.stream(Spliterators.spliteratorUnknownSize(new Iterator<Integer>() {
                        @Override
                        public boolean hasNext() {
                            return it1.hasNext();
                        }

                        @Override
                        public Integer next() {
                            return Math.max(it1.next(), it2.next());
                        }
                    }, Spliterator.IMMUTABLE), false).toList();
                });
    }

    private String formatLine(final List<String> data, final List<Integer> maxWidthPerColumn) {
        final var widthIt = maxWidthPerColumn.iterator();
        return data.stream()
                .map(it -> {
                    final int spaces = widthIt.next() - it.length();
                    return it + (spaces > 0 ? " ".repeat(spaces) : "");
                })
                .collect(joining(" | ", "| ", " |"));
    }

    public record TableOptions(boolean transpose, String lineSeparator) {
    }
}
