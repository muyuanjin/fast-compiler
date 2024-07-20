package com.muyuanjin.compiler.util;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author muyuanjin
 * @since 2024/5/13 9:54
 */
@UtilityClass
public class JMethods {
    /**
     * 获取方法
     *
     * @param targetClass    目标类
     * @param methodName     方法名
     * @param parameterTypes 参数类型
     * @return 方法
     */
    public static Method getMethod(Class<?> targetClass, String methodName, Class<?>... parameterTypes) {
        return getMethodValue(targetClass, methodName, parameterTypes).method;
    }

    @SneakyThrows
    public static Method searchMethods(Method[] methods, String name, Class<?>[] parameterTypes) {
        return (Method) searchMethods.invokeExact(methods, name, parameterTypes);
    }

    @SneakyThrows
    public static Method tryGetMethod(Class<?> targetClass, String methodName, Class<?>... parameterTypes) {
        return (Method) searchMethods.invokeExact(DECLARED_METHODS.get(targetClass), methodName, parameterTypes);
    }

    public static List<Method> getMethods(Class<?> targetClass) {
        return Collections.unmodifiableList(Arrays.asList(DECLARED_METHODS.get(targetClass)));
    }

    /**
     * 获取方法句柄，如果是实例方法，实例要作为第一个参数调用
     * <pre>{@code
     *      getMethodHandle(target.getClass(), "methodName", int.class).invokeExact(target, 1);
     * }</pre>
     *
     * @param targetClass    目标类
     * @param methodName     方法名
     * @param parameterTypes 参数类型
     * @return 方法句柄
     */
    public static MethodHandle getMethodHandle(Class<?> targetClass, String methodName, Class<?>... parameterTypes) {
        return getMethodValue(targetClass, methodName, parameterTypes).methodHandle;
    }

    /**
     * 获取方法返回值和参数类型都是Object的方法句柄，如果是实例方法，实例要作为第一个参数调用
     * <pre>{@code
     *      getObjMethodHandle(target, "methodName",int.class).invokeExact((Object) target,(Object) 1);
     * }</pre>
     *
     * @param targetClass    目标类
     * @param methodName     方法名
     * @param parameterTypes 参数类型
     * @return 方法返回值和参数类型都是Object的方法句柄
     */
    public static MethodHandle getObjMethodHandle(Class<?> targetClass, String methodName, Class<?>... parameterTypes) {
        return getMethodValue(targetClass, methodName, parameterTypes).objMethodHandle;
    }

    /**
     * 获取方法返回值是Object方法参数是Object[]（可变参数）的方法句柄，如果是实例方法，实例要作为第一个参数调用
     * <pre>{@code
     *      getArgsMethodHandle(target, "methodName", parameterTypes).invokeExact((Object) target, (Object[]) args);
     * }</pre>
     *
     * @param targetClass    目标类
     * @param methodName     方法名
     * @param parameterTypes 参数类型
     * @return 方法返回值是Object方法参数是Object[]（可变参数）的方法句柄
     */
    public static MethodHandle getArgsMethodHandle(Class<?> targetClass, String methodName, Class<?>... parameterTypes) {
        return getMethodValue(targetClass, methodName, parameterTypes).argsMethodHandle;
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    public static <T> T invokeStatic(Class<?> targetClass, String methodName, Class<?>[] parameterTypes, Object... args) {
        return (T) getArgsMethodHandle(targetClass, methodName, parameterTypes).invokeExact(args);
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    public static <T> T invokeStatic(Class<?> targetClass, String methodName, Object... args) {
        if (args.length == 0) {
            return invokeStatic(targetClass, methodName, EMPTY_CLASS_ARRAY, EMPTY_OBJECT_ARRAY);
        }
        return (T) findMethodValue(targetClass, methodName, args).argsMethodHandle.invokeExact(args);
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    public static <T> T invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        Object[] vars = new Object[args.length + 1];
        vars[0] = target;
        System.arraycopy(args, 0, vars, 1, args.length);
        return (T) getArgsMethodHandle(target.getClass(), methodName, parameterTypes).invokeExact(vars);
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    public static <T> T invoke(Object target, String methodName) {
        return (T) getObjMethodHandle(target.getClass(), methodName).invokeExact(target);
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    public static <T> T invoke(Object target, String methodName, Class<?> parameterType, Object arg1) {
        return (T) getObjMethodHandle(target.getClass(), methodName, parameterType).invokeExact(target, arg1);
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    public static <T> T invoke(Object target, String methodName, Class<?>[] parameterTypes, Object arg1, Object arg2) {
        return (T) getObjMethodHandle(target.getClass(), methodName, parameterTypes).invokeExact(target, arg1, arg2);
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    public static <T> T invoke(Object target, String methodName, Class<?>[] parameterTypes, Object arg1, Object arg2, Object arg3) {
        return (T) getObjMethodHandle(target.getClass(), methodName, parameterTypes).invokeExact(target, arg1, arg2, arg3);
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    public static <T> T invoke(Object target, String methodName, Class<?>[] parameterTypes, Object arg1, Object arg2, Object arg3, Object arg4) {
        return (T) getObjMethodHandle(target.getClass(), methodName, parameterTypes).invokeExact(target, arg1, arg2, arg3, arg4);
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    public static <T> T invoke(Object target, String methodName, Class<?>[] parameterTypes, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5) {
        return (T) getObjMethodHandle(target.getClass(), methodName, parameterTypes).invokeExact(target, arg1, arg2, arg3, arg4, arg5);
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    public static <T> T invoke(Object target, String methodName, Object arg1) {
        return (T) findMethodValue(target.getClass(), methodName, arg1).objMethodHandle.invokeExact(target, arg1);
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    public static <T> T invoke(Object target, String methodName, Object arg1, Object arg2) {
        return (T) findMethodValue(target.getClass(), methodName, arg1, arg2).objMethodHandle.invokeExact(target, arg1, arg2);
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    public static <T> T invoke(Object target, String methodName, Object arg1, Object arg2, Object arg3) {
        return (T) findMethodValue(target.getClass(), methodName, arg1, arg2, arg3).objMethodHandle.invokeExact(target, arg1, arg2, arg3);
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    public static <T> T invoke(Object target, String methodName, Object arg1, Object arg2, Object arg3, Object arg4) {
        return (T) findMethodValue(target.getClass(), methodName, arg1, arg2, arg3, arg4).objMethodHandle.invokeExact(target, arg1, arg2, arg3, arg4);
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    public static <T> T invoke(Object target, String methodName, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5) {
        return (T) findMethodValue(target.getClass(), methodName, arg1, arg2, arg3, arg4, arg5).objMethodHandle.invokeExact(target, arg1, arg2, arg3, arg4, arg5);
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    public static <T> T invoke(Object target, String methodName, Object... args) {
        if (args.length == 0) {
            return invoke(target, methodName, EMPTY_CLASS_ARRAY, EMPTY_OBJECT_ARRAY);
        }
        Object[] vars = new Object[args.length + 1];
        vars[0] = target;
        System.arraycopy(args, 0, vars, 1, args.length);
        return (T) findMethodValue(target.getClass(), methodName, args).argsMethodHandle.invokeExact(vars);
    }

    private static Method findMethod(Class<?> targetClass, String methodName, Object... args) {
        Method[] declaredMethods = DECLARED_METHODS.get(targetClass);
        List<Method> methods = new ArrayList<>();
        for (Method declaredMethod : declaredMethods) {
            if (declaredMethod.getName().equals(methodName)) {
                methods.add(declaredMethod);
            }
        }
        if (methods.size() == 1) {
            return methods.get(0);
        }
        out:
        for (Method method : methods) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length == args.length) {
                for (int i = 0; i < parameterTypes.length; i++) {
                    if (!getWrapperClass(parameterTypes[i]).isInstance(args[i])) {
                        continue out;
                    }
                }
                return method;
            } else {
                Parameter[] parameters = method.getParameters();
                if (parameters[parameters.length - 1].isVarArgs()) {
                    for (int i = 0; i < parameters.length - 1; i++) {
                        if (!getWrapperClass(parameterTypes[i]).isInstance(args[i])) {
                            continue out;
                        }
                    }
                    if (args.length == parameters.length - 1) {
                        return method;
                    }
                    Class<?> componentType = parameterTypes[parameters.length - 1].getComponentType();
                    for (int i = parameters.length - 1; i < args.length; i++) {
                        if (!getWrapperClass(componentType).isInstance(args[i])) {
                            continue out;
                        }
                    }
                    return method;
                }
            }
        }
        throw Throws.sneakyThrows(new NoSuchMethodError(methodName + " " + Arrays.toString(args)));
    }

    private static Class<?> getWrapperClass(Class<?> primitiveClass) {
        if (primitiveClass.isPrimitive()) {
            if (primitiveClass == int.class) return Integer.class;
            if (primitiveClass == boolean.class) return Boolean.class;
            if (primitiveClass == byte.class) return Byte.class;
            if (primitiveClass == char.class) return Character.class;
            if (primitiveClass == short.class) return Short.class;
            if (primitiveClass == long.class) return Long.class;
            if (primitiveClass == float.class) return Float.class;
            if (primitiveClass == double.class) return Double.class;
        }
        return primitiveClass;
    }

    private static MethodValue findMethodValue(Class<?> targetClass, String methodName, Object... args) {
        MethodKey key = new MethodKey(methodName, getParameterNames(args));
        return METHODS.get(targetClass).computeIfAbsent(key, k -> {
            try {
                Method method = findMethod(targetClass, methodName, args);
                return MethodValue.of(method, JUnsafe.IMPL_LOOKUP.unreflect(method));
            } catch (Throwable e) {
                throw Throws.sneakyThrows(e);
            }
        });
    }

    private static MethodValue getMethodValue(Class<?> targetClass, String methodName, Class<?>[] parameterTypes) {
        MethodKey key = new MethodKey(methodName, getParameterTypeNames(parameterTypes));
        return METHODS.get(targetClass).computeIfAbsent(key, k -> {
            try {
                Method method = (Method) searchMethods.invokeExact(DECLARED_METHODS.get(targetClass), methodName, parameterTypes);
                if (method == null) {
                    throw new NoSuchMethodException(methodName + " " + Arrays.toString(parameterTypes));
                }
                return MethodValue.of(method, JUnsafe.IMPL_LOOKUP.unreflect(method));
            } catch (Throwable e) {
                throw Throws.sneakyThrows(e);
            }
        });
    }

    private static List<String> getParameterNames(Object... args) {
        String[] names = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            names[i] = arg == null ? "null" : arg.getClass().getName();
        }
        return Arrays.asList(names);
    }

    private static List<String> getParameterTypeNames(Class<?>[] parameterTypes) {
        String[] names = new String[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            names[i] = parameterTypes[i].getName();
        }
        return Arrays.asList(names);
    }

    private static final Class<?>[] EMPTY_CLASS_ARRAY = new Class[0];
    private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];

    private static final MethodHandle copyMethods;
    private static final MethodHandle searchMethods;

    static {
        try {
            copyMethods = JUnsafe.IMPL_LOOKUP.findStatic(Class.class, "copyMethods", MethodType.methodType(Method[].class, Method[].class));
            searchMethods = JUnsafe.IMPL_LOOKUP.findStatic(Class.class, "searchMethods", MethodType.methodType(Method.class, Method[].class, String.class, Class[].class));
        } catch (Throwable e) {
            throw Throws.sneakyThrows(e);
        }
    }

    private static final ClassValue<Method[]> DECLARED_METHODS = new ClassValue<>() {
        @Override
        @SneakyThrows
        @SuppressWarnings("ConfusingArgumentToVarargsMethod")
        protected Method[] computeValue(Class<?> type) {
            Method[] declaredMethods = (Method[]) copyMethods.invokeExact(JUnsafe.getDeclaredMethods(type));
            for (Method declaredMethod : declaredMethods) {
                JUnsafe.setAccessible(declaredMethod);
            }
            return declaredMethods;
        }
    };

    private static final ClassValue<Map<MethodKey, MethodValue>> METHODS = new ClassValue<>() {
        @Override
        protected Map<MethodKey, MethodValue> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    private record MethodKey(String method, List<String> parameterTypes) {}

    /**
     * @param method           方法
     * @param methodHandle     原始方法句柄
     * @param objMethodHandle  方法返回值和参数类型都是Object的方法句柄
     * @param argsMethodHandle 方法返回值是Object方法参数是Object[]（可变参数）的方法句柄
     */
    private record MethodValue(Method method,
                               MethodHandle methodHandle,
                               MethodHandle objMethodHandle,
                               MethodHandle argsMethodHandle) {
        public static MethodValue of(Method method, MethodHandle methodHandle) {
            MethodType methodType = methodHandle.type();
            MethodHandle argsMethodHandle = methodHandle
                    .asType(methodType.changeReturnType(Object.class))
                    .asSpreader(Object[].class, methodType.parameterCount());
            return new MethodValue(method, methodHandle, methodHandle.asType(methodType.generic()), argsMethodHandle);
        }
    }
}