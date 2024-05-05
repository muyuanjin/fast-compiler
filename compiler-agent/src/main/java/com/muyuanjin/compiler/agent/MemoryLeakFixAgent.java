package com.muyuanjin.compiler.agent;

import jakarta.annotation.Nonnull;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.jar.asm.*;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.utility.JavaModule;

import java.io.IOException;
import java.io.StringReader;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static net.bytebuddy.jar.asm.Opcodes.*;

public class MemoryLeakFixAgent {
    public static void premain(String agentArgs, Instrumentation inst) throws IOException {
        Properties properties = System.getProperties();
        if (agentArgs != null && !agentArgs.isBlank()) {
            properties.load(new StringReader(agentArgs
                    .replace(",", "\n")
                    .replace("\\", "\\\\")
            ));
        }
        AgentBuilder agent = new AgentBuilder.Default();
        if (properties.getProperty("debug") != null) {
            String out = properties.getProperty("outputDir");
            if (out != null) {
                agent = agent.with(new AgentBuilder.Listener.Adapter() {
                    @Override
                    public void onTransformation(@Nonnull TypeDescription typeDescription, ClassLoader classLoader, JavaModule module, boolean loaded, @Nonnull DynamicType dynamicType) {
                        try {
                            Path path = Path.of(out, typeDescription.getName() + ".class");
                            Files.createDirectories(path.getParent());
                            Files.write(path, dynamicType.getBytes());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
            }
        }
        agent
                .type(ElementMatchers.nameContains("com.muyuanjin.compiler.impl.JavacTaskPool"))
                .transform((builder, typeDescription, classLoader, module, domain) -> builder
                        .defineField("_BY_AGENT", boolean.class, ACC_PUBLIC | ACC_STATIC | ACC_FINAL)
                        .value(true))
                .type(ElementMatchers.nameContains("com.sun.tools.javac.code.Scope$ScopeListenerList"))
                .transform((builder, typeDescription, classLoader, module, domain) -> builder
                        .visit(new AsmVisitorWrapper.AbstractBase() {
                            @Nonnull
                            @Override
                            public ClassVisitor wrap(@Nonnull TypeDescription instrumentedType,
                                                     @Nonnull ClassVisitor classVisitor,
                                                     @Nonnull Implementation.Context implementationContext,
                                                     @Nonnull TypePool typePool,
                                                     @Nonnull FieldList<FieldDescription.InDefinedShape> fields,
                                                     @Nonnull MethodList<?> methods,
                                                     int writerFlags,
                                                     int readerFlags) {
                                return new ClassVisitor(ASM9, null) {
                                    @Override
                                    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                                        @SuppressWarnings("UnnecessaryLocalVariable")
                                        ClassVisitor classWriter = classVisitor;
                                        FieldVisitor fieldVisitor;
                                        MethodVisitor methodVisitor;
                                        classWriter.visit(V17, ACC_PUBLIC | ACC_SUPER, "com/sun/tools/javac/code/Scope$ScopeListenerList", null, "java/lang/Object", null);

                                        classWriter.visitSource("Scope.java", null);

                                        classWriter.visitNestHost("com/sun/tools/javac/code/Scope");

                                        classWriter.visitInnerClass("com/sun/tools/javac/code/Scope$ScopeListenerList", "com/sun/tools/javac/code/Scope", "ScopeListenerList", ACC_PUBLIC | ACC_STATIC);

                                        classWriter.visitInnerClass("com/sun/tools/javac/code/Scope$ScopeListener", "com/sun/tools/javac/code/Scope", "ScopeListener", ACC_PUBLIC | ACC_STATIC | ACC_ABSTRACT | ACC_INTERFACE);

                                        {
                                            fieldVisitor = classWriter.visitField(0, "listeners", "Ljava/util/Set;", "Ljava/util/Set<Lcom/sun/tools/javac/code/Scope$ScopeListener;>;", null);
                                            fieldVisitor.visitEnd();
                                        }
                                        {
                                            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                                            methodVisitor.visitCode();
                                            Label label0 = new Label();
                                            methodVisitor.visitLabel(label0);
                                            methodVisitor.visitLineNumber(200, label0);
                                            methodVisitor.visitVarInsn(ALOAD, 0);
                                            methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                                            Label label1 = new Label();
                                            methodVisitor.visitLabel(label1);
                                            methodVisitor.visitLineNumber(201, label1);
                                            methodVisitor.visitVarInsn(ALOAD, 0);
                                            methodVisitor.visitTypeInsn(NEW, "java/util/WeakHashMap");
                                            methodVisitor.visitInsn(DUP);
                                            methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/util/WeakHashMap", "<init>", "()V", false);
                                            methodVisitor.visitMethodInsn(INVOKESTATIC, "java/util/Collections", "newSetFromMap", "(Ljava/util/Map;)Ljava/util/Set;", false);
                                            methodVisitor.visitFieldInsn(PUTFIELD, "com/sun/tools/javac/code/Scope$ScopeListenerList", "listeners", "Ljava/util/Set;");
                                            methodVisitor.visitInsn(RETURN);
                                            methodVisitor.visitMaxs(3, 1);
                                            methodVisitor.visitEnd();
                                        }
                                        {
                                            methodVisitor = classWriter.visitMethod(0, "add", "(Lcom/sun/tools/javac/code/Scope$ScopeListener;)V", null, null);
                                            methodVisitor.visitCode();
                                            Label label0 = new Label();
                                            methodVisitor.visitLabel(label0);
                                            methodVisitor.visitLineNumber(204, label0);
                                            methodVisitor.visitVarInsn(ALOAD, 0);
                                            methodVisitor.visitFieldInsn(GETFIELD, "com/sun/tools/javac/code/Scope$ScopeListenerList", "listeners", "Ljava/util/Set;");
                                            methodVisitor.visitVarInsn(ALOAD, 1);
                                            methodVisitor.visitMethodInsn(INVOKEINTERFACE, "java/util/Set", "add", "(Ljava/lang/Object;)Z", true);
                                            methodVisitor.visitInsn(POP);
                                            Label label1 = new Label();
                                            methodVisitor.visitLabel(label1);
                                            methodVisitor.visitLineNumber(205, label1);
                                            methodVisitor.visitInsn(RETURN);
                                            methodVisitor.visitMaxs(2, 2);
                                            methodVisitor.visitEnd();
                                        }
                                        {
                                            methodVisitor = classWriter.visitMethod(0, "symbolAdded", "(Lcom/sun/tools/javac/code/Symbol;Lcom/sun/tools/javac/code/Scope;)V", null, null);
                                            methodVisitor.visitCode();
                                            Label label0 = new Label();
                                            methodVisitor.visitLabel(label0);
                                            methodVisitor.visitLineNumber(208, label0);
                                            methodVisitor.visitVarInsn(ALOAD, 0);
                                            methodVisitor.visitVarInsn(ALOAD, 1);
                                            methodVisitor.visitVarInsn(ALOAD, 2);
                                            methodVisitor.visitInsn(ICONST_0);
                                            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "com/sun/tools/javac/code/Scope$ScopeListenerList", "walkReferences", "(Lcom/sun/tools/javac/code/Symbol;Lcom/sun/tools/javac/code/Scope;Z)V", false);
                                            Label label1 = new Label();
                                            methodVisitor.visitLabel(label1);
                                            methodVisitor.visitLineNumber(209, label1);
                                            methodVisitor.visitInsn(RETURN);
                                            methodVisitor.visitMaxs(4, 3);
                                            methodVisitor.visitEnd();
                                        }
                                        {
                                            methodVisitor = classWriter.visitMethod(0, "symbolRemoved", "(Lcom/sun/tools/javac/code/Symbol;Lcom/sun/tools/javac/code/Scope;)V", null, null);
                                            methodVisitor.visitCode();
                                            Label label0 = new Label();
                                            methodVisitor.visitLabel(label0);
                                            methodVisitor.visitLineNumber(212, label0);
                                            methodVisitor.visitVarInsn(ALOAD, 0);
                                            methodVisitor.visitVarInsn(ALOAD, 1);
                                            methodVisitor.visitVarInsn(ALOAD, 2);
                                            methodVisitor.visitInsn(ICONST_1);
                                            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "com/sun/tools/javac/code/Scope$ScopeListenerList", "walkReferences", "(Lcom/sun/tools/javac/code/Symbol;Lcom/sun/tools/javac/code/Scope;Z)V", false);
                                            Label label1 = new Label();
                                            methodVisitor.visitLabel(label1);
                                            methodVisitor.visitLineNumber(213, label1);
                                            methodVisitor.visitInsn(RETURN);
                                            methodVisitor.visitMaxs(4, 3);
                                            methodVisitor.visitEnd();
                                        }
                                        {
                                            methodVisitor = classWriter.visitMethod(ACC_PRIVATE, "walkReferences", "(Lcom/sun/tools/javac/code/Symbol;Lcom/sun/tools/javac/code/Scope;Z)V", null, null);
                                            methodVisitor.visitCode();
                                            Label label0 = new Label();
                                            methodVisitor.visitLabel(label0);
                                            methodVisitor.visitLineNumber(216, label0);
                                            methodVisitor.visitVarInsn(ALOAD, 0);
                                            methodVisitor.visitFieldInsn(GETFIELD, "com/sun/tools/javac/code/Scope$ScopeListenerList", "listeners", "Ljava/util/Set;");
                                            methodVisitor.visitMethodInsn(INVOKEINTERFACE, "java/util/Set", "iterator", "()Ljava/util/Iterator;", true);
                                            methodVisitor.visitVarInsn(ASTORE, 4);
                                            Label label1 = new Label();
                                            methodVisitor.visitLabel(label1);
                                            methodVisitor.visitFrame(Opcodes.F_APPEND, 1, new Object[]{"java/util/Iterator"}, 0, null);
                                            methodVisitor.visitVarInsn(ALOAD, 4);
                                            methodVisitor.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
                                            Label label2 = new Label();
                                            methodVisitor.visitJumpInsn(IFEQ, label2);
                                            methodVisitor.visitVarInsn(ALOAD, 4);
                                            methodVisitor.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);
                                            methodVisitor.visitTypeInsn(CHECKCAST, "com/sun/tools/javac/code/Scope$ScopeListener");
                                            methodVisitor.visitVarInsn(ASTORE, 5);
                                            Label label3 = new Label();
                                            methodVisitor.visitLabel(label3);
                                            methodVisitor.visitLineNumber(217, label3);
                                            methodVisitor.visitVarInsn(ILOAD, 3);
                                            Label label4 = new Label();
                                            methodVisitor.visitJumpInsn(IFEQ, label4);
                                            Label label5 = new Label();
                                            methodVisitor.visitLabel(label5);
                                            methodVisitor.visitLineNumber(218, label5);
                                            methodVisitor.visitVarInsn(ALOAD, 5);
                                            methodVisitor.visitVarInsn(ALOAD, 1);
                                            methodVisitor.visitVarInsn(ALOAD, 2);
                                            methodVisitor.visitMethodInsn(INVOKEINTERFACE, "com/sun/tools/javac/code/Scope$ScopeListener", "symbolRemoved", "(Lcom/sun/tools/javac/code/Symbol;Lcom/sun/tools/javac/code/Scope;)V", true);
                                            Label label6 = new Label();
                                            methodVisitor.visitJumpInsn(GOTO, label6);
                                            methodVisitor.visitLabel(label4);
                                            methodVisitor.visitLineNumber(220, label4);
                                            methodVisitor.visitFrame(Opcodes.F_APPEND, 1, new Object[]{"com/sun/tools/javac/code/Scope$ScopeListener"}, 0, null);
                                            methodVisitor.visitVarInsn(ALOAD, 5);
                                            methodVisitor.visitVarInsn(ALOAD, 1);
                                            methodVisitor.visitVarInsn(ALOAD, 2);
                                            methodVisitor.visitMethodInsn(INVOKEINTERFACE, "com/sun/tools/javac/code/Scope$ScopeListener", "symbolAdded", "(Lcom/sun/tools/javac/code/Symbol;Lcom/sun/tools/javac/code/Scope;)V", true);
                                            methodVisitor.visitLabel(label6);
                                            methodVisitor.visitLineNumber(222, label6);
                                            methodVisitor.visitFrame(Opcodes.F_CHOP, 1, null, 0, null);
                                            methodVisitor.visitJumpInsn(GOTO, label1);
                                            methodVisitor.visitLabel(label2);
                                            methodVisitor.visitLineNumber(223, label2);
                                            methodVisitor.visitFrame(Opcodes.F_CHOP, 1, null, 0, null);
                                            methodVisitor.visitInsn(RETURN);
                                            methodVisitor.visitMaxs(3, 6);
                                            methodVisitor.visitEnd();
                                        }
                                        classWriter.visitEnd();
                                    }
                                };
                            }
                        }))
                .installOn(inst);
    }
}
