package com.muyuanjin.compiler.impl;

import com.muyuanjin.compiler.CompilationProblem;
import com.muyuanjin.compiler.CompilationResult;
import com.muyuanjin.compiler.CompilationResult.Clazz;
import com.muyuanjin.compiler.JavaCompilerSettings;
import lombok.Getter;
import lombok.SneakyThrows;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Getter
public final class NativeJavaCompiler extends AbstractJavaCompiler {
    private static final JavacTaskPool TASK_POOL = new JavacTaskPool();
    private final JavacTaskPool taskPool;

    public NativeJavaCompiler() {
        this.taskPool = TASK_POOL;
    }

    public NativeJavaCompiler(int maxPoolSize) {
        this.taskPool = new JavacTaskPool(maxPoolSize);
    }

    public NativeJavaCompiler(int maxPoolSize, Duration maxAge) {
        this.taskPool = new JavacTaskPool(maxPoolSize, maxAge);
    }

    @Override
    @SneakyThrows
    public CompilationResult compile(Map<String, String> sources, ClassLoader classLoader, JavaCompilerSettings settings) {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        Charset charset = Charset.forName(settings.getSourceEncoding());

        final List<JavaFileObject> units = new ArrayList<>(sources.size());
        for (Map.Entry<String, String> entry : sources.entrySet()) {
            String javaFilePath = entry.getKey();
            if (!javaFilePath.endsWith(".java")) {
                javaFilePath = CompileUtil.toJavaResourcePath(javaFilePath);
            }
            units.add(new MemoryInputJavaFileObject(javaFilePath, entry.getValue()));
        }
        Iterable<String> options = settings.toJavacOptions();
        List<Clazz> classes = taskPool.getTask(diagnostics, null,
                charset, options, null, units, classLoader, (ctx, task) -> {
                    MemoryFileManager manager = ctx.get(MemoryFileManager.class);
                    if (task.call()) {
                        List<MemoryOutputJavaFileObject> outputs = manager.getOutputs();
                        List<Clazz> list = new ArrayList<>(outputs.size());
                        for (var output : outputs) {
                            String binaryName = output.getBinaryName();
                            list.add(new Clazz(CompileUtil.toClassResourcePath(binaryName), binaryName, output.toByteArray()));
                        }
                        return list;
                    }
                    return Collections.emptyList();
                });
        var builder = CompilationResult.builder();
        if (!classes.isEmpty()) {
            return builder.classes(classes).build();
        }
        var problems = diagnostics.getDiagnostics();
        for (var diagnostic : problems) {
            CompilationProblem problem = new CompilationProblem(diagnostic);
            if (problem.isError()) {
                builder.error(problem);
            } else {
                builder.warning(problem);
            }
        }
        return builder.build();
    }
}