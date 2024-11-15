package com.muyuanjin.compiler;

import com.muyuanjin.compiler.util.JUnsafe;
import lombok.Builder;
import lombok.Singular;
import lombok.SneakyThrows;

import java.security.SecureClassLoader;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

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

    public List<LoadedClazz> loadAll() {
        try (var loader = new ClazzLoader(classes)) {
            return loader.loadClass(classes);
        }
    }

    public <T> Class<T> load(String name) {
        return loadClass((clazz, classes) -> clazz.name.equals(name), "There is no class named " + name);
    }

    public <T> Class<T> load(Predicate<Clazz> predicate) {
        return loadClass((clazz, classes) -> predicate.test(clazz), "There is no class that satisfies the condition");
    }

    public <T> Class<T> loadFirst() {
        return loadClass((clazz, classes) -> classes.indexOf(clazz) == 0, "There are no classes");
    }

    public <T> Class<T> loadLast() {
        return loadClass((clazz, classes) -> classes.indexOf(clazz) == classes.size() - 1, "There are no classes");
    }

    public <T> Class<T> loadSingle() {
        return loadClass((clazz, classes) -> classes.size() == 1, "There are more than one class");
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    public <T> T loadInstance(String name) {
        return (T) JUnsafe.UNSAFE.allocateInstance(load(name));
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    public <T> T loadInstance(Predicate<Clazz> predicate) {
        return (T) JUnsafe.UNSAFE.allocateInstance(load(predicate));
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    public <T> T loadFirstInstance() {
        return (T) JUnsafe.UNSAFE.allocateInstance(loadFirst());
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    public <T> T loadLastInstance() {
        return (T) JUnsafe.UNSAFE.allocateInstance(loadLast());
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    public <T> T loadSingleInstance() {
        return (T) JUnsafe.UNSAFE.allocateInstance(loadSingle());
    }

    public record Clazz(String path, String name, byte[] bytes) {
    }

    public record LoadedClazz(String path, String name, byte[] bytes, Class<?> clazz) {
    }

    public static CompilationResult ofErrors(CompilationProblem... errors) {
        var builder = builder();
        for (CompilationProblem error : errors) {
            if (error.isError()) {
                builder.errors(List.of(error));
            } else {
                builder.warnings(List.of(error));
            }
        }
        return builder.build();
    }

    private <T> Class<T> loadClass(BiPredicate<Clazz, List<Clazz>> predicate, String errorMessage) {
        Clazz result = null;
        if (isSuccessful()) {
            for (Clazz clazz : classes) {
                if (predicate.test(clazz, classes)) {
                    if (result == null) {
                        result = clazz;
                    } else {
                        throw new IllegalStateException("There are more than one class satisfies the condition");
                    }
                }
            }
            if (result != null) {
                try (var loader = new ClazzLoader(classes)) {
                    return loader.loadClass(result.name, result.bytes);
                }
            }
        } else {
            throw new IllegalStateException("There are compilation errors:" + errors);
        }
        throw new IllegalStateException(errorMessage);
    }

    private static class ClazzLoader extends SecureClassLoader implements AutoCloseable {
        private Map<String, Clazz> sources;

        public ClazzLoader(Collection<Clazz> classes) {
            sources = new HashMap<>((int) ((float) classes.size() / 0.75f + 1));
            for (Clazz clazz : classes) {
                sources.put(clazz.name(), clazz);
            }
        }

        @SuppressWarnings("unchecked")
        public <T> Class<T> loadClass(String className, byte[] bytes) {
            Class<?> loadedClass = findLoadedClass(className);
            if (loadedClass != null) {
                return (Class<T>) loadedClass;
            }
            return (Class<T>) super.defineClass(className, bytes, 0, bytes.length);
        }

        public List<LoadedClazz> loadClass(Collection<Clazz> classes) {
            List<LoadedClazz> list = new ArrayList<>(classes.size());
            for (Clazz clazz : classes) {
                list.add(new LoadedClazz(clazz.path(), clazz.name(), clazz.bytes(), loadClass(clazz.name(), clazz.bytes())));
            }
            return list;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (sources != null) {
                Clazz clazz = sources.get(name);
                if (clazz != null) {
                    return loadClass(name, clazz.bytes());
                }
            }
            throw new ClassNotFoundException(name);
        }

        @Override
        public void close() {
            sources = null;
        }
    }
}