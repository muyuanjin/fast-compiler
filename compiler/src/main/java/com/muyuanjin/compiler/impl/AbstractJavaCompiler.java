package com.muyuanjin.compiler.impl;

import com.muyuanjin.compiler.CompilationResult;
import com.muyuanjin.compiler.JavaCompiler;
import com.muyuanjin.compiler.JavaCompilerSettings;

import java.util.Map;

abstract class AbstractJavaCompiler implements JavaCompiler {
    protected JavaCompilerSettings settings;

    @Override
    public void setSettings(JavaCompilerSettings settings) {
        this.settings = settings;
    }

    @Override
    public JavaCompilerSettings getSettings() {
        if (this.settings == null) {
            this.settings = new JavaCompilerSettings();
        }
        return this.settings;
    }

    protected ClassLoader getClassLoader() {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader == null) {
            contextClassLoader = getClass().getClassLoader();
        }
        return contextClassLoader;
    }

    @Override
    public CompilationResult compile(String slashPath, String sourceCode) {
        return compile(slashPath, sourceCode, getClassLoader(), getSettings());
    }

    @Override
    public CompilationResult compile(String slashPath, String sourceCode, ClassLoader classLoader) {
        return compile(slashPath, sourceCode, classLoader, getSettings());
    }

    @Override
    public CompilationResult compile(String slashPath, String sourceCode, ClassLoader classLoader, JavaCompilerSettings settings) {
        return compile(Map.of(slashPath, sourceCode), classLoader, settings);
    }

    @Override
    public CompilationResult compile(Map<String, String> sources) {
        return compile(sources, getClassLoader(), getSettings());
    }

    @Override
    public CompilationResult compile(Map<String, String> sources, ClassLoader classLoader) {
        return compile(sources, classLoader, getSettings());
    }
}
