package com.muyuanjin.compiler;

import lombok.Builder;
import lombok.Singular;

import java.util.List;

@Builder
public record CompilationResult(
        @Singular List<CompilationProblem> errors,
        @Singular List<CompilationProblem> warnings,
        @Singular("clazz") List<Clazz> classes
) {
    public CompilationResult {
        for (CompilationProblem error : errors) {
            if (!error.isError()) {
                throw new IllegalArgumentException("errors must be errors");
            }
        }
        for (CompilationProblem warning : warnings) {
            if (warning.isError()) {
                throw new IllegalArgumentException("warnings must be warnings");
            }
        }
    }

    public boolean isSuccessful() {
        return errors.isEmpty();
    }

    public <T> Class<T> loadSingle() {
        if (isSuccessful()) {
            if (classes.size() == 1) {
                Clazz next = classes.get(0);
                return JavaCompiler.loadClass(next.name, next.bytes);
            } else {
                throw new IllegalStateException("There are more than one class");
            }
        }
        throw new IllegalStateException("Compilation failed" + this);
    }

    public record Clazz(String path, String name, byte[] bytes) {
    }

    public static CompilationResult ofErrors(CompilationProblem... errors) {
        var builder = CompilationResult.builder();
        for (CompilationProblem error : errors) {
            if (error.isError()) {
                builder.errors(List.of(error));
            } else {
                builder.warnings(List.of(error));
            }
        }
        return builder.build();
    }
}