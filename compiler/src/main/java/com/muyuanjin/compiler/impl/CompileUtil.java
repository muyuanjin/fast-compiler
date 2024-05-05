package com.muyuanjin.compiler.impl;

import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

@UtilityClass
public class CompileUtil {
    /**
     * org/my/Class.xxx -> org.my.Class
     */
    public static String toClassName(String resourceName) {
        char[] charArray = resourceName.toCharArray();
        int lastDot = 0;
        for (int i = 0; i < charArray.length; i++) {
            switch (charArray[i]) {
                case '/' -> charArray[i] = '.';
                case '.' -> lastDot = i;
                case ':' -> throw new IllegalArgumentException("Invalid resource name: " + resourceName);
            }
        }
        return new String(charArray, 0, lastDot);
    }


    /**
     * org.my.Class -> org/my/Class.class
     */
    public static String toClassResourcePath(Class<?> cls) {
        return toResourcePath(cls.getName(), ".class");
    }

    /**
     * org.my.Class -> org/my/Class.class
     */
    public static String toClassResourcePath(String pName) {
        return toResourcePath(pName, ".class");
    }

    /**
     * org.my.Class -> org/my/Class.java
     */
    public static String toJavaResourcePath(String pName) {
        return toResourcePath(pName, ".java");
    }

    public static String toResourcePath(String pName, String suffix) {
        return toResourcePath("", pName, suffix);
    }

    /**
     * org.my.Class -> [prefix]org/my/Class[suffix]
     */
    public static String toResourcePath(String prefix, String name, String suffix) {
        int pLength = prefix.length();
        int mLength = pLength + name.length();
        int length = mLength + suffix.length();
        char[] chars = new char[length];
        for (int i = 0; i < pLength; i++) {
            chars[i] = prefix.charAt(i);
        }
        for (int i = pLength; i < mLength; i++) {
            char c = name.charAt(i - pLength);
            chars[i] = c == '.' ? '/' : c;
        }
        for (int i = mLength; i < length; i++) {
            chars[i] = suffix.charAt(i - mLength);
        }
        return new String(chars);
    }

    public static boolean isCaseSensitiveOS() {
        String os = System.getProperty("os.name").toUpperCase();
        return os.contains("WINDOWS") || os.contains("MAC OS X");
    }

    public static boolean isWindows() {
        String os = System.getProperty("os.name");
        return os.toUpperCase().contains("WINDOWS");
    }

    public static boolean isOSX() {
        String os = System.getProperty("os.name");
        return os.toUpperCase().contains("MAC OS X");
    }

    private static final Field MODIFIERS_FIELD;

    static {
        Field field = null;
        try {
            Method getDeclaredFields0 = Class.class.getDeclaredMethod("getDeclaredFields0", boolean.class);
            getDeclaredFields0.setAccessible(true);
            Field[] fields = (Field[]) getDeclaredFields0.invoke(Field.class, false);
            for (Field f1 : fields) {
                if (f1.getName().equals("modifiers")) {
                    f1.setAccessible(true);
                    field = f1;
                    break;
                }
            }
        } catch (Throwable ignored) {
        }
        MODIFIERS_FIELD = field;
    }

    @SneakyThrows
    public static void removeFinal(Field field) {
        if (MODIFIERS_FIELD == null) {
            throw new UnsupportedOperationException("Can't remove final modifier,please add " +
                                                    "--add-opens=java.base/java.lang=ALL-UNNAMED " +
                                                    "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED " +
                                                    "to your jvm options");
        }
        MODIFIERS_FIELD.setInt(field, field.getModifiers() & ~Modifier.FINAL);
    }

    private static final Field LISTENERS;
    private static final Field LIST_LISTENERS;
    private static final Field CLASSES;
    private static final Field SUB_SCOPES;
    private static final Field MEMBERS_CACHE;
    private static final Field NIL_SCOPE;

    static {
        try {
            LISTENERS = Scope.class.getDeclaredField("listeners");
            LISTENERS.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }

        try {
            LIST_LISTENERS = Scope.ScopeListenerList.class.getDeclaredField("listeners");
            LIST_LISTENERS.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }

        try {
            CLASSES = Symtab.class.getDeclaredField("classes");
            CLASSES.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }

        try {
            SUB_SCOPES = Scope.CompoundScope.class.getDeclaredField("subScopes");
            SUB_SCOPES.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }

        try {
            MEMBERS_CACHE = Types.class.getDeclaredField("membersCache");
            MEMBERS_CACHE.setAccessible(true);
            Class<?> aClass = Class.forName("com.sun.tools.javac.code.Types$MembersClosureCache");
            NIL_SCOPE = aClass.getDeclaredField("nilScope");
            NIL_SCOPE.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * JDK bug, {@link Types#newRound} 在清除缓存时，没有清除{@link Types.MembersClosureCache#nilScope}，会导致大量的内存泄露
     */
    @SneakyThrows
    @SuppressWarnings("JavadocReference")
    public static void clear(Types types) {
        NIL_SCOPE.set(MEMBERS_CACHE.get(types), null);
    }

    @NonNull
    @SneakyThrows
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Map<Symbol.ModuleSymbol, Symbol.ClassSymbol> remove(Symtab symtab, Name flatName) {
        Map<Name, Map<Symbol.ModuleSymbol, Symbol.ClassSymbol>> classes = (Map) CLASSES.get(symtab);
        if (classes == null) {
            return Collections.emptyMap();
        }
        return Objects.requireNonNullElse(classes.remove(flatName), Collections.emptyMap());
    }

    @SneakyThrows
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void clear(Symtab symtab) {
        Map<Name, Map<Symbol.ModuleSymbol, Symbol.ClassSymbol>> classes = (Map) CLASSES.get(symtab);
        if (classes == null) {
            return;
        }

        classes.values().parallelStream()
                .forEach(value -> {
                    for (Symbol.ClassSymbol classSymbol : value.values()) {
                        clear(classSymbol.members_field);
                    }
                });
    }

    /**
     * {@link Scope#listeners} ,{@link Scope.ScopeListenerList#add} 没有清理 失效的 weakReference，累积之后会导致内存泄漏
     */
    @SneakyThrows
    @SuppressWarnings({"unchecked", "JavadocReference", "rawtypes"})
    public static void clear(Scope scope) {
        if (scope == null) {
            return;
        }
        if (scope instanceof Scope.CompoundScope compoundScope) {
            ListBuffer<Scope> o1 = (ListBuffer) SUB_SCOPES.get(compoundScope);
            o1.forEach(CompileUtil::clear);
        }
        Scope.ScopeListenerList listenerList = (Scope.ScopeListenerList) LISTENERS.get(scope);
        if (listenerList == null) {
            return;
        }
        List<WeakReference<Scope.ScopeListener>> first = (List) LIST_LISTENERS.get(listenerList);
        if (first == null || first.isEmpty()) {
            return;
        }

        List<WeakReference<Scope.ScopeListener>> current;

        // 使用for循环和tail手动遍历链表，移除失效的WeakReference
        List<WeakReference<Scope.ScopeListener>> prev = null;
        for (current = first; current != null; current = current.tail) {
            if (current.head == null || current.head.get() == null) {
                // 引用已失效
                if (prev != null) {
                    prev.tail = current.tail;  // 移除当前节点
                } else {
                    first = current.tail;  // 头节点失效，移动头指针
                }
            } else {
                prev = current;  // 更新前一个有效的节点
            }
        }
        if (first == null) {
            first = List.nil();
        }
        LIST_LISTENERS.set(listenerList, first);
    }
}
