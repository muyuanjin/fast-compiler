/*
 * This file is part of Burningwave Core.
 *
 * Author: Roberto Gentili
 *
 * Hosted at: https://github.com/burningwave/core
 *
 * --
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Roberto Gentili
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without
 * limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.muyuanjin.compiler.util;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/*
 * Copyright (c) Burningwave Core. and/or Roberto Gentili.
 * The original file is org.burningwave.core.classes.Modules
 * Modifications made by muyuanjin on 2024/5/14.
 */
@UtilityClass
@SuppressWarnings("DuplicatedCode")
public class JModules {
    private static final Class<?> moduleClass;
    private static final Set<?> allSet;
    private static final Set<?> everyOneSet;
    private static final Set<?> allUnnamedSet;
    private static final Map<String, ?> nameToModule;

    static {
        try {
            ClassLoader loader = JModules.class.getClassLoader() == null ? ClassLoader.getSystemClassLoader() : JModules.class.getClassLoader();
            moduleClass = JUnsafe.getClassByName("java.lang.Module", false, loader, JModules.class);
            Class<?> moduleLayerClass = JUnsafe.getClassByName("java.lang.ModuleLayer", false, loader, JModules.class);
            Object moduleLayer = JMethods.invokeStatic(moduleLayerClass, "boot");
            nameToModule = JFields.getValue(moduleLayer, "nameToModule");
            allSet = new HashSet<>();
            allSet.add(JFields.getStaticValue(moduleClass, "ALL_UNNAMED_MODULE"));
            allSet.add(JFields.getStaticValue(moduleClass, "EVERYONE_MODULE"));
            everyOneSet = new HashSet<>();
            everyOneSet.add(JFields.getStaticValue(moduleClass, "EVERYONE_MODULE"));
            allUnnamedSet = new HashSet<>();
            allUnnamedSet.add(JFields.getStaticValue(moduleClass, "ALL_UNNAMED_MODULE"));
        } catch (Throwable e) {
            throw Throws.sneakyThrows(e);
        }
    }

    private static volatile boolean initialized = false;

    @SneakyThrows
    public static synchronized void makeSureExported() {
        if (!initialized) {
            exportAllToAll();
            initialized = true;
        }
    }

    public static synchronized void exportAllToAll() {
        try {
            nameToModule.forEach((name, module) -> (JMethods.<Set<String>>invoke(module, "getPackages")).forEach(pkgName -> {
                exportToAll("exportedPackages", module, pkgName);
                exportToAll("openPackages", module, pkgName);
            }));
        } catch (Throwable e) {
            throw Throws.sneakyThrows(e);
        }
    }


    public static synchronized void exportToAllUnnamed(String name) {
        exportTo(name, JModules::exportToAllUnnamed);
    }

    public static synchronized void exportToAll(String name) {
        exportTo(name, JModules::exportToAll);
    }

    public static synchronized void exportPackage(String moduleFromName, String moduleToName, String... packageNames) {
        Object moduleFrom = checkAndGetModule(moduleFromName);
        Object moduleTo = checkAndGetModule(moduleToName);
        exportPackage(moduleFrom, moduleTo, packageNames);
    }


    public static synchronized void exportPackageToAll(String moduleFromName, String... packageNames) {
        Object moduleFrom = checkAndGetModule(moduleFromName);
        exportPackage(moduleFrom, everyOneSet.iterator().next(), packageNames);
    }


    public static synchronized void exportPackageToAllUnnamed(String moduleFromName, String... packageNames) {
        Object moduleFrom = checkAndGetModule(moduleFromName);
        exportPackage(moduleFrom, allUnnamedSet.iterator().next(), packageNames);
    }


    public static synchronized void export(String moduleFromName, String moduleToName) {
        try {
            Object moduleFrom = checkAndGetModule(moduleFromName);
            Object moduleTo = checkAndGetModule(moduleToName);
            (JMethods.<Set<String>>invoke(moduleFrom, "getPackages")).forEach(pkgName -> {
                export("exportedPackages", moduleFrom, pkgName, moduleTo);
                export("openPackages", moduleFrom, pkgName, moduleTo);
            });
        } catch (Throwable e) {
            throw Throws.sneakyThrows(e);
        }
    }


    static void exportPackage(Object moduleFrom, Object moduleTo, String... packageNames) {
        Set<String> modulePackages = JMethods.invoke(moduleFrom, "getPackages");
        Stream.of(packageNames).forEach(pkgName -> {
            if (!modulePackages.contains(pkgName)) {
                throw new PackageNotFoundException("Package " + pkgName + " not found in module " + JFields.getValue(moduleFrom, "name"));
            }
            export("exportedPackages", moduleFrom, pkgName, moduleTo);
            export("openPackages", moduleFrom, pkgName, moduleTo);
        });
    }

    static Object checkAndGetModule(String name) {
        Object module = nameToModule.get(name);
        if (module == null) {
            throw new NotFoundException("Module named name " + name + " not found");
        }
        return module;
    }

    static void exportTo(String name, ThrConsumer<String, Object, String> exporter) {
        try {
            Object module = checkAndGetModule(name);
            (JMethods.<Set<String>>invoke(module, "getPackages")).forEach(pkgName -> {
                exporter.accept("exportedPackages", module, pkgName);
                exporter.accept("openPackages", module, pkgName);
            });
        } catch (Throwable e) {
            throw Throws.sneakyThrows(e);
        }
    }


    static void exportToAll(String fieldName, Object module, String pkgName) {
        Map<String, Set<?>> pckgForModule = JFields.getValue(module, fieldName);
        if (pckgForModule == null) {
            pckgForModule = new HashMap<>();
            JFields.setValue(module, fieldName, pckgForModule);
        }
        pckgForModule.put(pkgName, allSet);
        if (fieldName.startsWith("exported")) {
            JMethods.invokeStatic(moduleClass, "addExportsToAll0", module, pkgName);
        }
    }


    static void exportToAllUnnamed(String fieldName, Object module, String pkgName) {
        Map<String, Set<?>> pckgForModule = JFields.getValue(module, fieldName);
        if (pckgForModule == null) {
            pckgForModule = new HashMap<>();
            JFields.setValue(module, fieldName, pckgForModule);
        }
        pckgForModule.put(pkgName, allUnnamedSet);
        if (fieldName.startsWith("exported")) {
            JMethods.invokeStatic(moduleClass, "addExportsToAllUnnamed0", module, pkgName);
        }
    }

    static void export(String fieldName, Object moduleFrom, String pkgName, Object moduleTo) {
        Map<String, Set<Object>> pckgForModule = JFields.getValue(moduleFrom, fieldName);
        if (pckgForModule == null) {
            pckgForModule = new HashMap<>();
            JFields.setValue(moduleFrom, fieldName, pckgForModule);
        }
        Set<Object> moduleSet = pckgForModule.get(pkgName);
        if (!(moduleSet instanceof HashSet)) {
            if (moduleSet != null) {
                moduleSet = new HashSet<>(moduleSet);
            } else {
                moduleSet = new HashSet<>();
            }
            pckgForModule.put(pkgName, moduleSet);
        }
        moduleSet.add(moduleTo);
        if (fieldName.startsWith("exported")) {
            JMethods.invokeStatic(moduleClass, "addExports0", moduleFrom, pkgName, moduleTo);
        }
    }

    @FunctionalInterface
    interface ThrConsumer<T, U, R> {
        void accept(T t, U u, R r);
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }

    public static class PackageNotFoundException extends RuntimeException {
        public PackageNotFoundException(String message) {
            super(message);
        }
    }
}
