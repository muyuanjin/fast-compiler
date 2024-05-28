package com.muyuanjin.compiler.util;


import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import sun.misc.Unsafe;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 本类用于突破 java 的强封装，通过 unsafe（由于太多框架依赖 unsafe ，故 unsafe 被默认无条件-add-opens 给所有模块，通过 unsafe 一步步突破封装）
 *
 * @author muyuanjin
 * @since 2024/5/14 13:00
 */
@UtilityClass
public class JUnsafe {
    private static final int overrideOffset;
    public static final Unsafe UNSAFE;

    static {
        /*
         * 通过反射获取 Unsafe 实例，这是JDK故意保留的使用方式
         * 通过 Unsafe setAccessible 的 Field 和 未 setAccessible 的 Field 逐一对比获取 override 字段的内存偏移（字段偏移在所有子类型中固定）
         * 通过 override 偏移，即可绕过权限校验强行设置所有 setAccessible
         */
        try {
            Field accessible = Unsafe.class.getDeclaredField("theUnsafe");
            Field notAccessible = Unsafe.class.getDeclaredField("theUnsafe");
            accessible.setAccessible(true);
            notAccessible.setAccessible(false);
            Unsafe unsafe = (Unsafe) accessible.get(null);
            // override 布尔型字节偏移量。在java17应该是 12
            int i = 0;
            while (unsafe.getBoolean(accessible, i) == unsafe.getBoolean(notAccessible, i)) {i++;}
            overrideOffset = i;
            UNSAFE = unsafe;
        } catch (Throwable e) {
            throw Throws.sneakyThrows(e);
        }
    }

    @SuppressWarnings({"deprecation", "UnusedReturnValue"})
    public static <T extends AccessibleObject> T setAccessible(T object) {
        if (object == null) {
            return null;
        }
        if (object.isAccessible()) {
            return object;
        }
        UNSAFE.putBoolean(object, overrideOffset, true);
        return object;
    }

    private static final MethodHandle newLookUp;

    static {
        //noinspection SpellCheckingInspection
        try {
            // 通过反射获取 MethodHandles.Lookup 的构造方法
            Constructor<MethodHandles.Lookup> constructor =
                    MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, Class.class, int.class);
            setAccessible(constructor);
            // 获取构造方法的 MethodHandle ，MethodHandle 只在获取时检查权限
            // setAccessible 之后 unreflect 不再检查权限，任意 lookup 均可
            newLookUp = MethodHandles.lookup().unreflectConstructor(constructor);
        } catch (Throwable e) {
            throw Throws.sneakyThrows(e);
        }
    }

    //使用 ClassValue 缓存持有 Class 强引用的对象，防止 Class 无法卸载导致内存泄漏
    private static final ClassValue<MethodHandles.Lookup> LOOKUP = new ClassValue<>() {
        @Override
        @SneakyThrows
        protected MethodHandles.Lookup computeValue(Class<?> type) {
            return (MethodHandles.Lookup) newLookUp.invokeExact(type, (Class<?>) null, /*Lookup.TRUSTED*/-1);
        }
    };

    static final MethodHandles.Lookup IMPL_LOOKUP = LOOKUP.get(Object.class);

    /**
     * 获取拥有 指定类的最高权限 MethodHandles.Lookup 对象
     */
    @SneakyThrows
    public static MethodHandles.Lookup getLookUp(Class<?> lookupClass) {
        return LOOKUP.get(lookupClass);
    }

    private static final MethodHandle getDeclaredMethods0;
    private static final MethodHandle getDeclaredFields0;
    private static final MethodHandle forName0;

    static {
        try {
            getDeclaredMethods0 = IMPL_LOOKUP.findSpecial(Class.class, "getDeclaredMethods0",
                    MethodType.methodType(Method[].class, boolean.class), Class.class);
            getDeclaredFields0 = IMPL_LOOKUP.findSpecial(Class.class, "getDeclaredFields0",
                    MethodType.methodType(Field[].class, boolean.class), Class.class);
            forName0 = IMPL_LOOKUP.findStatic(Class.class, "forName0",
                    MethodType.methodType(Class.class, String.class, boolean.class, ClassLoader.class, Class.class));
        } catch (Throwable e) {
            throw Throws.sneakyThrows(e);
        }
    }

    @SneakyThrows
    public static Method[] getDeclaredMethods(Class<?> clazz) {
        return (Method[]) getDeclaredMethods0.invokeExact(clazz, false);
    }

    @SneakyThrows
    public static Field[] getDeclaredFields(Class<?> clazz) {
        return (Field[]) getDeclaredFields0.invokeExact(clazz, false);
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    public static <T> Class<T> getClassByName(String className, boolean initialize, ClassLoader classLoader, Class<?> caller) {
        return (Class<T>) forName0.invokeExact(className, initialize, classLoader, caller);
    }
}