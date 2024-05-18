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
        return Arrays.asList(DECLARED_FIELDS.get(clazz));
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
            MethodHandles.Lookup lookUp = JUnsafe.getLookUp(declaringClass);
            for (Field field : fields) {
                if (field.getName().equals(name)) {
                    return new FieldInfo(field, lookUp.unreflectVarHandle(field), Modifier.isVolatile(field.getModifiers()));
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
            MethodHandles.Lookup lookUp = JUnsafe.getLookUp(Class.class);
            copyFields = lookUp.findStatic(Class.class, "copyFields", MethodType.methodType(Field[].class, Field[].class));
        } catch (Throwable e) {
            throw Throws.sneakyThrows(e);
        }
    }

    private static final ClassValue<Field[]> DECLARED_FIELDS = new ClassValue<>() {
        private static final VarHandle trustedFinal;

        static {
            try {
                MethodHandles.Lookup lookUp = JUnsafe.getLookUp(Field.class);
                trustedFinal = lookUp.findVarHandle(Field.class, "trustedFinal", boolean.class);
            } catch (Throwable e) {
                throw Throws.sneakyThrows(e);
            }
        }

        @Override
        @SneakyThrows
        @SuppressWarnings("ConfusingArgumentToVarargsMethod")
        protected Field[] computeValue(Class<?> type) {
            Field[] declaredFields = (Field[]) copyFields.invokeExact(JUnsafe.getDeclaredFields(type));
            for (Field declaredField : declaredFields) {
                JUnsafe.setAccessible(declaredField);
                trustedFinal.set(declaredField, false);
            }
            return declaredFields;
        }
    };

    public record FieldInfo(Field field, VarHandle varHandle, boolean isVolatile) {}
}
