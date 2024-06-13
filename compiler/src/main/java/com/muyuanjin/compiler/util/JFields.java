package com.muyuanjin.compiler.util;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author muyuanjin
 * @since 2024/5/13 10:35
 */
@UtilityClass
public class JFields {
    public static Field getField(Class<?> clazz, String name) {
        return getFieldInfo(clazz, name).field;
    }

    public static VarHandle getVarHandle(Class<?> clazz, String name) {
        return getFieldInfo(clazz, name).varHandle;
    }

    public static FieldInfo getFieldInfo(Class<?> clazz, String name) {
        return FIELDS.get(clazz).computeIfAbsent(name, key -> searchFields(DECLARED_FIELDS.get(clazz), key));
    }

    public static List<Field> getFields(Class<?> clazz) {
        return Collections.unmodifiableList(Arrays.asList(DECLARED_FIELDS.get(clazz)));
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    public static <T> T getValue(Object target, String name) {
        FieldInfo fieldValue = getFieldInfo(target.getClass(), name);
        if (!fieldValue.isVolatile) {
            return (T) fieldValue.varHandle.get(target);
        }
        return (T) fieldValue.varHandle.getVolatile(target);
    }

    public static void setValue(Object target, String name, Object value) {
        FieldInfo fieldValue = getFieldInfo(target.getClass(), name);
        if (!fieldValue.isVolatile) {
            fieldValue.varHandle.set(target, value);
        }
        fieldValue.varHandle.setVolatile(target, value);
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    public static <T> T getStaticValue(Class<?> clazz, String name) {
        FieldInfo fieldValue = getFieldInfo(clazz, name);
        if (!fieldValue.isVolatile) {
            return (T) fieldValue.varHandle.get();
        }
        return (T) fieldValue.varHandle.getVolatile();
    }

    public static void setStaticValue(Class<?> clazz, String name, Object value) {
        FieldInfo fieldValue = getFieldInfo(clazz, name);
        if (!fieldValue.isVolatile) {
            fieldValue.varHandle.set(value);
        }
        fieldValue.varHandle.setVolatile(value);
    }

    @SneakyThrows
    private static FieldInfo searchFields(Field[] fields, String name) {
        if (fields.length != 0) {
            Class<?> declaringClass = fields[0].getDeclaringClass();
            for (Field field : fields) {
                if (field.getName().equals(name)) {
                    return FieldInfo.of(field);
                }
            }
        }
        throw Throws.sneakyThrows(new NoSuchFieldException(name));
    }

    private static final ClassValue<Map<String, FieldInfo>> FIELDS = new ClassValue<>() {
        @Override
        protected Map<String, FieldInfo> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    private static final MethodHandle copyFields;

    static {
        try {
            copyFields = JUnsafe.IMPL_LOOKUP.findStatic(Class.class, "copyFields", MethodType.methodType(Field[].class, Field[].class));
        } catch (Throwable e) {
            throw Throws.sneakyThrows(e);
        }
    }

    private static final ClassValue<Field[]> DECLARED_FIELDS = new ClassValue<>() {
        @Override
        @SneakyThrows
        @SuppressWarnings("ConfusingArgumentToVarargsMethod")
        protected Field[] computeValue(Class<?> type) {
            Field[] declaredFields = (Field[]) copyFields.invokeExact(JUnsafe.getDeclaredFields(type));
            for (Field declaredField : declaredFields) {
                JUnsafe.setAccessible(declaredField);
            }
            return declaredFields;
        }
    };

    public record FieldInfo(Field field, VarHandle varHandle, boolean isVolatile) {
        private static final MethodHandle makeFieldHandle;
        private static final MethodHandle newMemberName;
        private static final MethodHandle getFieldType;

        static {
            try {
                ClassLoader loader = FieldInfo.class.getClassLoader() == null ? ClassLoader.getSystemClassLoader() : FieldInfo.class.getClassLoader();
                Class<Object> varHandlesClass = JUnsafe.getClassByName("java.lang.invoke.VarHandles", true, loader, MethodHandles.class);
                Class<Object> memberNameClass = JUnsafe.getClassByName("java.lang.invoke.MemberName", true, loader, MethodHandles.class);
                makeFieldHandle = JUnsafe.IMPL_LOOKUP.findStatic(varHandlesClass, "makeFieldHandle",
                                MethodType.methodType(VarHandle.class, memberNameClass, Class.class, Class.class, boolean.class))
                        .asType(MethodType.methodType(VarHandle.class, Object.class, Class.class, Class.class, boolean.class));
                newMemberName = JUnsafe.IMPL_LOOKUP.unreflectConstructor(memberNameClass.getConstructor(Field.class, boolean.class))
                        .asType(MethodType.methodType(Object.class, Field.class, boolean.class));
                getFieldType = JUnsafe.IMPL_LOOKUP.findVirtual(memberNameClass, "getFieldType", MethodType.methodType(Class.class))
                        .asType(MethodType.methodType(Class.class, Object.class));
            } catch (Throwable e) {
                throw Throws.sneakyThrows(e);
            }
        }

        @SneakyThrows
        public static FieldInfo of(Field field) {
            Class<?> clazz = field.getDeclaringClass();
            Object memberName = newMemberName.invokeExact(field, false);
            // 绕过 trustedFinal 检查
            VarHandle handle = (VarHandle) makeFieldHandle.invokeExact(memberName, clazz, (Class<?>) getFieldType.invokeExact(memberName), true);
            return new FieldInfo(field, handle, Modifier.isVolatile(field.getModifiers()));
        }
    }
}
