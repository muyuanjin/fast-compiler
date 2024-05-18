package com.muyuanjin.compiler;

import com.muyuanjin.compiler.impl.EclipseJavaCompiler;
import com.muyuanjin.compiler.impl.NativeJavaCompiler;
import com.muyuanjin.compiler.util.JMethods;
import org.codehaus.commons.compiler.CompilerFactoryFactory;
import org.codehaus.commons.compiler.ICompilerFactory;
import org.codehaus.commons.compiler.ISimpleCompiler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.function.BiFunction;

class JavaCompilerTest {
    private static final String lambdaSource = """
            import java.util.function.BiFunction;
            public class LambdaContainer {
                public static BiFunction<Integer, Integer, Integer> getLambda() {
                    return (x, y) -> x + y;
                }
            }
            """;

    @BeforeAll
    public static void setup() {
        System.out.println(NativeJavaCompiler.MODIFY_BY_AGENT);
    }

    @Test
    public void nativeJavaCompiler() {
        CompilationResult compile = JavaCompiler.NATIVE.compile("LambdaContainer.java", lambdaSource);
        Assertions.assertEquals(3,
                JMethods.<BiFunction<Integer, Integer, Integer>>
                                invokeStatic(compile.loadSingle(), "getLambda").apply(1, 2));
    }

    @Test
    public void eclipseJavaCompiler() {
        CompilationResult compile = new EclipseJavaCompiler().compile("LambdaContainer.java", lambdaSource);
        Assertions.assertEquals(3,
                JMethods.<BiFunction<Integer, Integer, Integer>>
                        invokeStatic(compile.loadSingle(), "getLambda").apply(1, 2));
    }

    @Test
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
        Class<?> lambdaContainer = iSimpleCompiler.getClassLoader().loadClass("LambdaContainer");
        Assertions.assertEquals(3,
                JMethods.<BiFunction<Integer, Integer, Integer>>
                        invokeStatic(lambdaContainer, "getLambda").apply(1, 2));
    }
}