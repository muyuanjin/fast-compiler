package com.muyuanjin.compiler;

import lombok.Builder;
import org.eclipse.jdt.core.compiler.IProblem;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

@Builder
public record CompilationProblem (
        String fileName,
        String message,
        boolean isError,
        int startLine,
        int endLine,
        int startColumn,
        int endColumn
) {
    public static CompilationProblem notFound(String sourceName) {
        return new CompilationProblem(sourceName, "Source [" + sourceName + "] not found", true, 0, 0, 0, 0);
    }

    public CompilationProblem {
        if (fileName == null) {
            throw new IllegalArgumentException("fileName cannot be null");
        }
        if (startLine < 1) {
            throw new IllegalArgumentException("startLine must be greater than 0");
        }
        if (startColumn < 1) {
            throw new IllegalArgumentException("startColumn must be greater than 0");
        }
        if (endLine < 1) {
            throw new IllegalArgumentException("endLine must be greater than 0");
        }
        if (endColumn < 1) {
            throw new IllegalArgumentException("endColumn must be greater than 0");
        }
        if (message == null) {
            throw new IllegalArgumentException("message cannot be null");
        }
    }

    public CompilationProblem(IProblem problem) {
        this(
                new String(problem.getOriginatingFileName()),
                problem.getMessage(),
                problem.isError(),
                problem.getSourceLineNumber(),
                problem.getSourceLineNumber(),
                problem.getSourceStart(),
                problem.getSourceEnd()
        );
    }

    public CompilationProblem(Diagnostic<? extends JavaFileObject> problem) {
        this(
                problem.getSource() == null ? "UNKNOWN" : problem.getSource().getName().substring(1),
                problem.getMessage(null),
                problem.getKind() == Diagnostic.Kind.ERROR,
                (int) problem.getLineNumber(),
                (int) problem.getLineNumber(),
                (int) problem.getColumnNumber(),
                (int) problem.getColumnNumber()
        );
    }

    @Override
    public String toString() {
        return this.fileName() + " (" +
               this.startLine() +
               ":" +
               this.startColumn() +
               ") : " +
               this.message();
    }
}
