/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.muyuanjin.compiler.impl;


import com.muyuanjin.compiler.util.JFields;
import com.muyuanjin.compiler.util.JMethods;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskEvent.Kind;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.api.*;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Type.ClassType;
import com.sun.tools.javac.comp.*;
import com.sun.tools.javac.file.CacheFSInfo;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.main.Arguments;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.platform.PlatformDescription;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.LetExpr;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.DefinedBy.Api;
import lombok.SneakyThrows;

import javax.tools.*;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.invoke.MethodHandle;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.List;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * A pool of reusable JavacTasks. When a task is no valid anymore, it is returned to the pool,
 * and its Context may be reused for future processing in some cases. The reuse is achieved
 * by replacing some components (most notably JavaCompiler and Log) with reusable counterparts,
 * and by cleaning up leftovers from previous compilation.
 * <p>
 * For each combination of options, a separate task/context is created and kept, as most option
 * values are cached inside components themselves.
 * <p>
 * When the compilation redefines sensitive classes (e.g. classes in the the java.* packages), the
 * task/context is not reused.
 * <p>
 * When the task is reused, then packages that were already listed won't be listed again.
 * <p>
 * Care must be taken to only return tasks that won't be used by the original caller.
 * <p>
 * Care must also be taken when custom components are installed, as those are not cleaned when the
 * task/context is reused, and subsequent getTask may return a task based on a context with these
 * custom components.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 */
/*
 * Copyright (c) Oracle and/or its affiliates.
 * Licensed under the GNU General Public License version 2 only, with Classpath exception.
 * Modifications made by muyuanjin on 2024/5/5.
 */
public class JavacTaskPool {
    static final boolean MODIFY_BY_AGENT;

    static {
        boolean modify;
        try {
            //noinspection JavaReflectionMemberAccess
            JavacTaskPool.class.getDeclaredField("_BY_AGENT");// add by agent
            modify = true;
        } catch (NoSuchFieldException e) {
            modify = false;
        }
        MODIFY_BY_AGENT = modify;
    }

    private static final JavacTool systemProvider = JavacTool.create();
    private static final Queue<ReusableContext> EMPTY_QUEUE = new ArrayDeque<>(0);
    private static final MethodHandle CLEANUP = JMethods.getMethodHandle(JavacTaskImpl.class, "cleanup");

    static {
        JFields.setValue(Source.Feature.MODULES, "maxLevel", Source.JDK1_2);
    }

    private final long maxAge;
    private final int maxPoolSize;
    private final Map<List<String>, Queue<ReusableContext>> options2Contexts = new HashMap<>();

    private int statReused = 0;
    private int statNew = 0;
    private int statPolluted = 0;
    private int statRemoved = 0;

    public JavacTaskPool() {
        this(Runtime.getRuntime().availableProcessors() * 2, Duration.ofDays(1));
    }

    /**
     * Creates the pool.
     *
     * @param maxPoolSize maximum number of tasks/context that will be kept in the pool.
     */
    public JavacTaskPool(int maxPoolSize) {
        this(maxPoolSize, Duration.ofDays(1));
    }

    public JavacTaskPool(int maxPoolSize, Duration maxAge) {
        this.maxPoolSize = maxPoolSize;
        this.maxAge = maxAge.toMillis();
    }

    /**
     * Creates a new task as if by {@link javax.tools.JavaCompiler#getTask} and runs the provided
     * worker with it. The task is only valid while the worker is running. The internal structures
     * may be reused from some previous compilation.
     *
     * @param diagnosticListener a diagnostic listener; if {@code
     *                           null} use the compiler's default method for reporting
     *                           diagnostics
     * @param options            compiler options, {@code null} means no options
     * @param classes            names of classes to be processed by annotation
     *                           processing, {@code null} means no class names
     * @param compilationUnits   the compilation units to compile, {@code
     *                           null} means no compilation units
     * @param worker             that should be run with the task
     * @return an object representing the compilation
     * @throws RuntimeException         if an unrecoverable error
     *                                  occurred in a user supplied component.  The
     *                                  {@linkplain Throwable#getCause() cause} will be the error in
     *                                  user code.
     * @throws IllegalArgumentException if any of the options are invalid,
     *                                  or if any of the given compilation units are of other kind than
     *                                  {@linkplain JavaFileObject.Kind#SOURCE source}
     */
    @SneakyThrows
    public <Z> Z getTask(DiagnosticListener<? super JavaFileObject> diagnosticListener,
                         Locale locale,
                         Charset charset,
                         Iterable<String> options,
                         Iterable<String> classes,
                         Iterable<? extends JavaFileObject> compilationUnits,
                         ClassLoader classLoader,
                         BiFunction<Context, JavacTask, Z> worker) {
        List<String> opts =
                StreamSupport.stream(options.spliterator(), false)
                        .collect(Collectors.toCollection(ArrayList::new));

        ReusableContext ctx;

        synchronized (this) {
            Queue<ReusableContext> cached =
                    options2Contexts.getOrDefault(opts, EMPTY_QUEUE);

            if (cached.isEmpty()) {
                ctx = new ReusableContext(opts);
                statNew++;
            } else {
                ctx = cached.remove();
                statReused++;
            }
        }

        ctx.useCount++;

        ClientCodeWrapper ccw = ClientCodeWrapper.instance(ctx);
        if (diagnosticListener != null) {
            ctx.put(DiagnosticListener.class, ccw.wrap(diagnosticListener));
        }
        MemoryFileManager memoryFileManager = ctx.get(MemoryFileManager.class);
        if (memoryFileManager == null) {
            ctx.put(Locale.class, locale);
            CacheFSInfo.preRegister(ctx);
            memoryFileManager = new MemoryFileManager(new JavacFileManager(ctx, false, charset), classLoader);
            ctx.put(MemoryFileManager.class, memoryFileManager);
        } else {
            memoryFileManager.setClassLoader(classLoader);
            memoryFileManager.setContext(ctx);
            memoryFileManager.setCharset(charset);
        }
        ctx.put(Log.errKey, new PrintWriter(Writer.nullWriter()));
        JavacTaskImpl task =
                (JavacTaskImpl) systemProvider.getTask(null, memoryFileManager, null,
                        opts, classes, compilationUnits, ctx);

        task.addTaskListener(ctx);

        Throwable ex = null;
        Z result = null;
        try {
            Types types = Types.instance(ctx);
            if (!MODIFY_BY_AGENT) {
                CompileUtil.clear(types);
            }
            // 这个cache 会导致速度大量下降，所以禁用
            //noinspection rawtypes,unchecked
            types.candidatesCache.cache = new HashMap() {
                @Override
                public Object put(Object key, Object value) {
                    return null;
                }
            };
            result = worker.apply(ctx, task);
        } catch (Throwable e) {
            ex = e;
        } finally {
            //additional cleanup: purge the compiled package:
            Symtab symtab = Symtab.instance(ctx);
            Names names = Names.instance(ctx);
            Symbol.ModuleSymbol module = symtab.java_base == symtab.noModule ? symtab.noModule
                    : symtab.unnamedModule;
            Symbol.Completer completer = ClassFinder.instance(ctx).getCompleter();
            List<MemoryOutputJavaFileObject> outputs = memoryFileManager.getOutputs();
            for (MemoryOutputJavaFileObject output : outputs) {
                String binaryName = output.getBinaryName();
                Symbol.ClassSymbol aClass = symtab.getClass(module, names.fromString(binaryName));
                if (aClass != null) {
                    for (Symbol.ClassSymbol value : CompileUtil.remove(symtab, aClass.flatName()).values()) {
                        value.packge().members_field = null;
                        value.packge().completer = completer;
                    }
                } else {
                    Symbol.PackageSymbol aPackage = symtab.getPackage(module, names.fromString(binaryName.substring(0, binaryName.lastIndexOf('.'))));
                    if (aPackage != null) {
                        for (Symbol.ClassSymbol clazz : symtab.getAllClasses()) {
                            if (clazz.packge() == aPackage) {
                                for (Symbol.ClassSymbol value : CompileUtil.remove(symtab, clazz.flatName()).values()) {
                                    value.packge().members_field = null;
                                    value.packge().completer = completer;
                                }
                            }
                        }
                        aPackage.members_field = null;
                        aPackage.completer = completer;
                    }
                }
            }
            if (!MODIFY_BY_AGENT) {
                // 清理 Scope 中可能的未清理资源
                CompileUtil.clear(symtab);
            }
        }

        //not returning the context to the pool if task crashes with an exception
        //the task/context may be in a broken state
        ctx.clear();
        if (ctx.polluted || ex != null) {
            statPolluted++;
            memoryFileManager.doClose();// close the file manager
        } else {
            CLEANUP.invokeExact(task);
            long currentTime = System.currentTimeMillis();
            if (ctx.timeStamp == 0) {
                ctx.timeStamp = currentTime;
            }
            synchronized (this) {
                ReusableContext toRemove;
                while ((toRemove = oldestContext()) != null && ((currentTime - toRemove.timeStamp > maxAge) || cacheSize() + 1 > maxPoolSize)) {
                    toRemove.get(MemoryFileManager.class).doClose();// close the file manager
                    options2Contexts.get(toRemove.arguments).remove(toRemove);
                    statRemoved++;
                }
                options2Contexts.computeIfAbsent(ctx.arguments, x -> new ArrayDeque<>()).add(ctx);
            }
        }
        if (ex != null) {
            throw ex;
        }
        return result;
    }

    //where:
    private long cacheSize() {
        long sum = 0L;
        for (Queue<ReusableContext> reusableContexts : options2Contexts.values()) {
            sum += reusableContexts.size();
        }
        return sum;
    }

    private ReusableContext oldestContext() {
        ReusableContext oldest = null;
        for (Queue<ReusableContext> value : options2Contexts.values()) {
            for (ReusableContext context : value) {
                if (oldest == null || context.timeStamp < oldest.timeStamp) {
                    oldest = context;
                }
            }
        }
        return oldest;
    }


    public void printStatistics(PrintStream out) {
        out.println(statReused + " reused Contexts");
        out.println(statNew + " newly created Contexts");
        out.println(statPolluted + " polluted Contexts");
        out.println(statRemoved + " removed Contexts");
    }


    static class ReusableContext extends Context implements TaskListener {

        Set<CompilationUnitTree> roots = new HashSet<>();

        List<String> arguments;
        boolean polluted = false;

        int useCount;
        long timeStamp;

        ReusableContext(List<String> arguments) {
            super();
            this.arguments = arguments;
            put(Log.logKey, ReusableLog.factory);
            put(JavaCompiler.compilerKey, ReusableJavaCompiler.factory);
        }

        void clear() {
            //when patching modules (esp. java.base), it may be impossible to
            //clear the symbols read from the patch path:
            polluted |= get(JavaFileManager.class).hasLocation(StandardLocation.PATCH_MODULE_PATH);
            drop(Arguments.argsKey);
            drop(DiagnosticListener.class);
            drop(Log.outKey);
            drop(Log.errKey);
            drop(JavaFileManager.class);
            drop(JavacTask.class);
            drop(JavacTrees.class);
            drop(JavacElements.class);
            drop(PlatformDescription.class);

            if (ht.get(Log.logKey) instanceof ReusableLog) {
                //log already inited - not first round
                ((ReusableLog) Log.instance(this)).clear();
                Enter.instance(this).newRound();
                ((ReusableJavaCompiler) ReusableJavaCompiler.instance(this)).clear();
                Types.instance(this).newRound();
                Check.instance(this).newRound();
                Check.instance(this).clear(); //clear mandatory warning handlers
                Preview.instance(this).clear(); //clear mandatory warning handlers
                Modules.instance(this).newRound();
                Annotate.instance(this).newRound();

                get(MemoryFileManager.class).newRound();// clear the file manager outputs

                CompileStates.instance(this).clear();
                MultiTaskListener.instance(this).clear();
                Options.instance(this).clear();

                //find if any of the roots have redefined java.* classes
                Symtab syms = Symtab.instance(this);
                pollutionScanner.scan(roots, syms);
                roots.clear();
            }
        }

        /**
         * This scanner detects as to whether the shared context has been polluted. This happens
         * whenever a compiled program redefines a core class (in 'java.*' package) or when
         * (typically because of cyclic inheritance) the symbol kind of a core class has been touched.
         */
        TreeScanner<Void, Symtab> pollutionScanner = new TreeScanner<>() {
            @Override
            @DefinedBy(Api.COMPILER_TREE)
            public Void scan(Tree tree, Symtab symtab) {
                if (tree instanceof LetExpr letExpr) {
                    scan(letExpr.defs, symtab);
                    scan(letExpr.expr, symtab);
                    return null;
                } else {
                    return super.scan(tree, symtab);
                }
            }

            @Override
            @DefinedBy(Api.COMPILER_TREE)
            public Void visitClass(ClassTree node, Symtab syms) {
                Symbol sym = ((JCClassDecl) node).sym;
                if (sym != null) {
                    syms.removeClass(sym.packge().modle, sym.flatName());
                    Type sup = supertype(sym);
                    if (isCoreClass(sym) ||
                        (sup != null && isCoreClass(sup.tsym) && sup.tsym.kind != Kinds.Kind.TYP)) {
                        polluted = true;
                    }
                }
                return super.visitClass(node, syms);
            }

            private boolean isCoreClass(Symbol s) {
                return s.flatName().toString().startsWith("java.");
            }

            private Type supertype(Symbol s) {
                if (s.type == null ||
                    !s.type.hasTag(TypeTag.CLASS)) {
                    return null;
                } else {
                    ClassType ct = (ClassType) s.type;
                    return ct.supertype_field;
                }
            }
        };

        @Override
        @DefinedBy(Api.COMPILER_TREE)
        public void finished(TaskEvent e) {
            if (e.getKind() == Kind.PARSE) {
                roots.add(e.getCompilationUnit());
            }
        }

        @Override
        @DefinedBy(Api.COMPILER_TREE)
        public void started(TaskEvent e) {
            //do nothing
        }

        <T> void drop(Key<T> k) {
            ht.remove(k);
        }

        <T> void drop(Class<T> c) {
            ht.remove(key(c));
        }

        /**
         * Reusable JavaCompiler; exposes a method to clean up the component from leftovers associated with
         * previous compilations.
         */
        static class ReusableJavaCompiler extends JavaCompiler {

            static final Factory<JavaCompiler> factory = ReusableContext.ReusableJavaCompiler::new;

            ReusableJavaCompiler(Context context) {
                super(context);
            }

            @Override
            public void close() {
                //do nothing
            }

            void clear() {
                newRound();
            }

            @Override
            protected void checkReusable() {
                //do nothing - it's ok to reuse the compiler
            }
        }

        /**
         * Reusable Log; exposes a method to clean up the component from leftovers associated with
         * previous compilations.
         */
        static class ReusableLog extends Log {

            static final Factory<Log> factory = ReusableLog::new;

            Context context;

            ReusableLog(Context context) {
                super(context);
                this.context = context;
            }

            void clear() {
                recorded.clear();
                sourceMap.clear();
                nerrors = 0;
                nwarnings = 0;
                //Set a fake listener that will lazily lookup the context for the 'real' listener. Since
                //this field is never updated when a new task is created, we cannot simply reset the field
                //or keep old value. This is a hack to workaround the limitations in the current infrastructure.
                diagListener = new DiagnosticListener<>() {
                    DiagnosticListener<JavaFileObject> cachedListener;

                    @Override
                    @DefinedBy(Api.COMPILER)
                    @SuppressWarnings("unchecked")
                    public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
                        if (cachedListener == null) {
                            cachedListener = context.get(DiagnosticListener.class);
                        }
                        cachedListener.report(diagnostic);
                    }
                };
            }
        }
    }
}
