package com.muyuanjin.compiler;

import com.muyuanjin.compiler.impl.NativeJavaCompiler;

import java.security.SecureClassLoader;
import java.util.Map;

public interface JavaCompiler {
    JavaCompilerSettings getSettings();

    void setSettings(JavaCompilerSettings settings);


    CompilationResult compile(String filename, String sourceCode);

    CompilationResult compile(String filename, String sourceCode, ClassLoader classLoader);

    CompilationResult compile(String filename, String sourceCode, ClassLoader classLoader, JavaCompilerSettings settings);


    CompilationResult compile(Map<String, String> sources);

    CompilationResult compile(Map<String, String> sources, ClassLoader classLoader);

    CompilationResult compile(Map<String, String> sources, ClassLoader classLoader, JavaCompilerSettings settings);

    NativeJavaCompiler NATIVE = new Object() {
        NativeJavaCompiler instance() {
            try {return new NativeJavaCompiler();} catch (NoClassDefFoundError e) {
                return null;
            }
        }
    }.instance();

//    EclipseJavaCompiler ECLIPSE = new Object() {
//        EclipseJavaCompiler instance() {
//            try {return new EclipseJavaCompiler();} catch (NoClassDefFoundError e) {
//                return null;
//            }
//        }
//    }.instance();

    static <T> Class<T> loadClass(String className, byte[] bytes) {
        return new SecureClassLoader() {
            @SuppressWarnings("unchecked")
            public Class<T> loadClass(String className, byte[] bytes) {
                return (Class<T>) super.defineClass(className, bytes, 0, bytes.length);
            }
        }.loadClass(className, bytes);
    }
}