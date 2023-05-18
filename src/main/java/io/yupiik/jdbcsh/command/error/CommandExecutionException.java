package io.yupiik.jdbcsh.command.error;

public class CommandExecutionException extends RuntimeException{
    public CommandExecutionException(final Throwable cause) {
        super(cause);
    }
}
