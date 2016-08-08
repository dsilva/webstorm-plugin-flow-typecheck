package com.github.dsilva.flowtc;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

public class Error {
    private final @NotNull String message;
    @NotNull String message() { return message; }

    private final @NotNull TextRange range;
    @NotNull TextRange range() { return range; }

    public Error(final @NotNull String msg, final @NotNull TextRange textRange) {
        this.message = msg;
        this.range = textRange;
    }
}
