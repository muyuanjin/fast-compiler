package com.muyuanjin.compiler;

import com.muyuanjin.compiler.impl.EclipseJavaCompiler;
import com.muyuanjin.compiler.impl.JavacTaskPool;
import org.codehaus.commons.compiler.CompilerFactoryFactory;
import org.codehaus.commons.compiler.ICompilerFactory;
import org.codehaus.commons.compiler.ISimpleCompiler;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.nio.file.Path;

@State(Scope.Benchmark)
public class JMH_JavaCompiler {
    public static void main(String[] args) throws RunnerException {
        Path basedir = Path.of(System.getProperty("basedir", System.getProperty("user.dir")));
        // 需要先 maven package compiler-agent
        Path agentPath = basedir.resolve("compiler-agent\\target\\compiler-agent-1.0-SNAPSHOT-jar-with-dependencies.jar");

        Options opt = new OptionsBuilder()
                .jvmArgsAppend(("""
                        -Dfile.encoding=UTF-8
                        -Dsun.jnu.encoding=UTF-8
                        --add-opens=java.base/java.lang=ALL-UNNAMED
                        --add-opens=java.base/java.lang.reflect=ALL-UNNAMED
                        --add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED
                        --add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED
                        --add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED
                        --add-opens=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED
                        --add-opens=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED
                        --add-opens=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED
                        --add-opens=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED
                        --add-opens=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED
                        --add-opens=jdk.compiler/com.sun.tools.javac.platform=ALL-UNNAMED
                        """).lines().toArray(String[]::new))
                .include(JMH_JavaCompiler.class.getSimpleName())
                .mode(Mode.Throughput)
                .forks(1)
                .jvmArgsPrepend("-javaagent:" + agentPath.toAbsolutePath()
//                                + "=debug,outputDir=F:\\JavaSnapshot\\agent"
                )
                .build();

        new Runner(opt).run();
    }

    private static final String lambdaSource = """
            import java.util.function.BiFunction;
            public class LambdaContainer {
                public static BiFunction<Integer, Integer, Integer> getLambda() {
                    return (x, y) -> x + y;
                }
            }
            """;

    @Setup
    public void setup() {
        System.out.print(JavacTaskPool.MODIFY_BY_AGENT);
    }

    @Benchmark
    public void nativeJavaCompiler() {
        CompilationResult compile = JavaCompiler.NATIVE.compile("LambdaContainer.java", lambdaSource);
    }

    @Benchmark
    public void eclipseJavaCompiler() {
        CompilationResult compile = new EclipseJavaCompiler().compile("LambdaContainer.java", lambdaSource);
    }

    @Benchmark
    public void janinoJavaCompiler() throws Exception {
        ICompilerFactory compilerFactory = CompilerFactoryFactory.getDefaultCompilerFactory(getClass().getClassLoader());
        ISimpleCompiler iSimpleCompiler = compilerFactory.newSimpleCompiler();
        iSimpleCompiler.setSourceVersion(17);
        iSimpleCompiler.setTargetVersion(17);

        iSimpleCompiler.cook(
                """
                        import java.util.function.BiFunction;
                        public class LambdaContainer {
                            public static BiFunction<Object, Object, Object> getLambda() {
                                return new BiFunction<Object, Object, Object>() {
                                    @Override
                                    public Object apply(Object x, Object y) {return ((Integer)x) + ((Integer)y);}
                                };
                            }
                        }
                        """
        );
    }
}
