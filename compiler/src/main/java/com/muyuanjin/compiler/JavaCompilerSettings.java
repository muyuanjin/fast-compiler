package com.muyuanjin.compiler;

import lombok.Data;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class JavaCompilerSettings {
    private String targetVersion = "17";
    private String sourceVersion = "17";
    private String sourceEncoding = "UTF-8";
    private String sourceFolder = "src/main/java/";
    private boolean warnings = false;
    private boolean deprecations = false;
    private boolean debug = false;
    private List<File> classpath;

    private List<String> javacOptions = new ArrayList<>();
    private Map<String, String> eclipseSettings = new HashMap<>();

    {
        eclipseSettings.put(CompilerOptions.OPTION_LineNumberAttribute, CompilerOptions.GENERATE);
        eclipseSettings.put(CompilerOptions.OPTION_SourceFileAttribute, CompilerOptions.GENERATE);
        eclipseSettings.put(CompilerOptions.OPTION_ReportUnusedImport, CompilerOptions.IGNORE);
        eclipseSettings.put(CompilerOptions.OPTION_LocalVariableAttribute, CompilerOptions.GENERATE);
    }

    public List<String> toJavacOptions() {
        List<String> options = new ArrayList<>();
        options.add("-source");
        options.add(getSourceVersion());
        options.add("-target");
        options.add(getTargetVersion());
        options.add("-encoding");
        options.add(getSourceEncoding());
        if (isDeprecations()) {
            options.add("-deprecation");
        }
        if (isDebug()) {
            options.add("-g");
        }
        if (isWarnings()) {
            options.add("-Xlint:all");
        }
        options.add("-proc:none");// 禁用所有注解处理器
        options.addAll(getJavacOptions());
        return options;
    }

    public Map<String, String> toEclipseOptions() {
        Map<String, String> map = new HashMap<>(eclipseSettings);
        map.put(CompilerOptions.OPTION_SuppressWarnings, isWarnings() ? CompilerOptions.GENERATE : CompilerOptions.DO_NOT_GENERATE);
        map.put(CompilerOptions.OPTION_ReportDeprecation, isDeprecations() ? CompilerOptions.GENERATE : CompilerOptions.DO_NOT_GENERATE);
        map.put(CompilerOptions.OPTION_TargetPlatform, getTargetVersion());
        map.put(CompilerOptions.OPTION_Source, getSourceVersion());
        map.put(CompilerOptions.OPTION_Compliance, getSourceVersion());
        map.put(CompilerOptions.OPTION_Encoding, getSourceEncoding());
        return map;
    }
}