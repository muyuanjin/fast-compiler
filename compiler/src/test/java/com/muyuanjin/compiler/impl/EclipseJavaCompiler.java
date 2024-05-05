/*
 * Copyright (c) 2020. Red Hat, Inc. and/or its affiliates.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.muyuanjin.compiler.impl;

import com.muyuanjin.compiler.CompilationProblem;
import com.muyuanjin.compiler.CompilationResult;
import com.muyuanjin.compiler.CompilationResult.Clazz;
import com.muyuanjin.compiler.JavaCompilerSettings;
import lombok.Getter;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.internal.compiler.*;
import org.eclipse.jdt.internal.compiler.Compiler;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFormatException;
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit;
import org.eclipse.jdt.internal.compiler.env.INameEnvironment;
import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblemFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
/*
 * Modifications made by muyuanjin on 2024/5/5.
 */
public class EclipseJavaCompiler extends AbstractJavaCompiler {
    public String getPathName(String fullPath) {
        String sourceFolder = getSettings().getSourceFolder();
        if (sourceFolder.isEmpty()) {
            return fullPath;
        }
        if (fullPath.startsWith(sourceFolder)) {
            return fullPath.substring(sourceFolder.length());
        }
        if (fullPath.charAt(0) == '/' && fullPath.startsWith(sourceFolder, 1)) {
            return fullPath.substring(sourceFolder.length() + 1);
        }
        return fullPath;
    }

    @Override
    public com.muyuanjin.compiler.CompilationResult compile(Map<String, String> sources, ClassLoader classLoader, JavaCompilerSettings settings) {
        ICompilationUnit[] compilationUnits = new ICompilationUnit[sources.size()];
        int index = 0;
        for (var entry : sources.entrySet()) {
            String sourceFile = entry.getKey();
            String bytes = entry.getValue();
            if (bytes == null) {
                return com.muyuanjin.compiler.CompilationResult.ofErrors(CompilationProblem.notFound(sourceFile));
            }
            compilationUnits[index++] = new CompilationUnit(bytes, sourceFile);
        }

        List<CompilationProblem> problems = new ArrayList<>();
        final IErrorHandlingPolicy policy = DefaultErrorHandlingPolicies.proceedWithAllProblems();
        final IProblemFactory problemFactory = new DefaultProblemFactory(Locale.getDefault());

        Map<String, byte[]> result = new HashMap<>((int) ((float) sources.size() / 0.75f + 1));

        final INameEnvironment nameEnvironment = new INameEnvironment() {
            @Override
            public NameEnvironmentAnswer findType(final char[][] pCompoundTypeName) {
                final StringBuilder result = new StringBuilder();
                for (int i = 0; i < pCompoundTypeName.length; i++) {
                    if (i != 0) {
                        result.append('.');
                    }
                    result.append(pCompoundTypeName[i]);
                }

                return findType(result.toString());
            }

            @Override
            public NameEnvironmentAnswer findType(final char[] pTypeName, final char[][] pPackageName) {
                final StringBuilder result = new StringBuilder();
                for (char[] chars : pPackageName) {
                    result.append(chars);
                    result.append('.');
                }

                result.append(pTypeName);
                return findType(result.toString());
            }

            private NameEnvironmentAnswer findType(final String pClazzName) {
                final String resourceName = CompileUtil.toClassResourcePath(pClazzName);
                final byte[] clazzBytes = result.get(resourceName);
                if (clazzBytes != null) {
                    try {
                        return createNameEnvironmentAnswer(pClazzName, clazzBytes);
                    } catch (final ClassFormatException e) {
                        throw new RuntimeException("ClassFormatException in loading class '" + pClazzName + "' with JCI.");
                    }
                }

                try (InputStream is = classLoader.getResourceAsStream(resourceName)) {
                    if (is == null) {
                        return null;
                    }

                    if (CompileUtil.isCaseSensitiveOS()) {
                        //  检查它确实是一个类，此问题是由于类 org.kie.Process 和路径 org/droosl/process 的 Windows 大小写敏感问题造成的
                        try {
                            classLoader.loadClass(pClazzName);
                        } catch (ClassNotFoundException | NoClassDefFoundError e) {
                            return null;
                        }
                    }

                    final byte[] buffer = new byte[8192];
                    try (ByteArrayOutputStream stream = new ByteArrayOutputStream(buffer.length)) {
                        int count;
                        while ((count = is.read(buffer, 0, buffer.length)) > 0) {
                            stream.write(buffer, 0, count);
                        }
                        stream.flush();
                        return createNameEnvironmentAnswer(pClazzName, stream.toByteArray());
                    }
                } catch (final IOException e) {
                    throw new RuntimeException("could not read class", e);
                } catch (final ClassFormatException e) {
                    throw new RuntimeException("wrong class format", e);
                }
            }

            private NameEnvironmentAnswer createNameEnvironmentAnswer(final String pClazzName, final byte[] clazzBytes) throws ClassFormatException {
                final char[] fileName = pClazzName.toCharArray();
                final ClassFileReader classFileReader = new ClassFileReader(clazzBytes, fileName, true);
                return new NameEnvironmentAnswer(classFileReader, null);
            }

            private boolean isSourceAvailable(final String pClazzName) {
                // FIXME: this should not be tied to the extension
                final String javaSource = CompileUtil.toJavaResourcePath(pClazzName);
                final String classSource = CompileUtil.toClassResourcePath(pClazzName);
                return result.containsKey(getSettings().getSourceFolder() + javaSource) || result.containsKey(getSettings().getSourceFolder() + classSource);
            }

            private boolean isPackage(final String pClazzName) {
                try (InputStream is = classLoader.getResourceAsStream(CompileUtil.toClassResourcePath(pClazzName))) {
                    if (is != null) {
                        if (CompileUtil.isWindows() || CompileUtil.isOSX()) {
                            // 检查它确实是一个类，此问题是由于类 org.kie.Process 和路径 org/droosl/process 的 Windows 大小写敏感问题造成的
                            try {
                                Class<?> cls = classLoader.loadClass(pClazzName);
                                if (cls != null) {
                                    return false;
                                }
                            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                                return true;
                            }
                        }
                    }
                    return is == null && !isSourceAvailable(pClazzName);
                } catch (IOException e) {
                    throw new RuntimeException("Cannot open or close resource stream!", e);
                }
            }

            @Override
            public boolean isPackage(char[][] parentPackageName, char[] pPackageName) {
                final StringBuilder result = new StringBuilder();
                if (parentPackageName != null) {
                    for (int i = 0; i < parentPackageName.length; i++) {
                        if (i != 0) {
                            result.append('.');
                        }
                        result.append(parentPackageName[i]);
                    }
                }

                if (parentPackageName != null && parentPackageName.length > 0) {
                    result.append('.');
                }
                result.append(pPackageName);
                return isPackage(result.toString());
            }

            @Override
            public void cleanup() {
            }
        };
        var builder = CompilationResult.builder();
        final ICompilerRequestor icompilerRequestor = pResult -> {
            if (pResult.hasProblems()) {
                final IProblem[] iProblems = pResult.getProblems();
                for (final IProblem iproblem : iProblems) {
                    final CompilationProblem problem = new CompilationProblem(iproblem);
                    problems.add(problem);
                }
            }
            if (!pResult.hasErrors()) {
                final ClassFile[] clazzFiles = pResult.getClassFiles();
                for (final ClassFile clazzFile : clazzFiles) {
                    final char[][] compoundName = clazzFile.getCompoundName();
                    final StringBuilder clazzName = new StringBuilder();
                    for (int j = 0; j < compoundName.length; j++) {
                        if (j != 0) {
                            clazzName.append('.');
                        }
                        clazzName.append(compoundName[j]);
                    }
                    result.put(clazzName.toString(), clazzFile.getBytes());
                }
            }
        };

        var settingsMap = settings.toEclipseOptions();
        CompilerOptions compilerOptions = new CompilerOptions(settingsMap);
        compilerOptions.parseLiteralExpressionsAsConstants = false;

        var compiler = new Compiler(nameEnvironment, policy, compilerOptions, icompilerRequestor, problemFactory);

        compiler.compile(compilationUnits);
        for (CompilationProblem problem : problems) {
            if (problem.isError()) {
                builder.error(problem);
            } else {
                builder.warning(problem);
            }
        }
        for (var entry : result.entrySet()) {
            //把类名转换为资源名
            builder.clazz(new Clazz(CompileUtil.toClassResourcePath(entry.getKey()), entry.getKey(), entry.getValue()));
        }
        return builder.build();
    }

    @Getter
    final class CompilationUnit implements ICompilationUnit {
        private final String fsFileName;
        private final String clazzName;
        private final String fileName;
        private final char[] typeName;
        private final char[][] packageName;
        private final String contents;

        CompilationUnit(String contents, final String pSourceFile) {
            this.contents = contents;

            this.fsFileName = pSourceFile;
            String decode = decode(getPathName(pSourceFile));
            this.clazzName = CompileUtil.toClassName(decode);

            this.fileName = decode(pSourceFile);
            int dot = this.clazzName.lastIndexOf('.');
            if (dot > 0) {
                this.typeName = this.clazzName.substring(dot + 1).toCharArray();
            } else {
                this.typeName = this.clazzName.toCharArray();
            }
            StringTokenizer tokenizer = new StringTokenizer(this.clazzName, ".");
            this.packageName = new char[tokenizer.countTokens() - 1][];
            for (int i = 0; i < this.packageName.length; i++) {
                this.packageName[i] = tokenizer.nextToken().toCharArray();
            }
        }

        private String decode(final String path) {
            return URLDecoder.decode(path, StandardCharsets.UTF_8);
        }

        public char[] getFileName() {
            return fileName.toCharArray();
        }

        public char[] getContents() {
            if (this.contents == null) {
                return null;
            }
            return this.contents.toCharArray();
        }

        public char[] getMainTypeName() {
            return typeName;
        }

        public boolean ignoreOptionalProblems() {
            return true;
        }
    }
}
