package io.yupiik.jdbcsh.io;

import java.io.InputStream;
import java.io.PrintStream;

public record StdIO(PrintStream stdout, PrintStream stderr, InputStream stdin) {
}
