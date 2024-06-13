package com.muyuanjin.compiler.util;

import org.junit.jupiter.api.Assertions;
import sun.misc.Unsafe;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;

/**
 * @author muyuanjin
 * @since 2024/6/13 14:24
 */
class JUnsafeTest {
    private static final ALog log = new ALog(1);

    public static void main(String[] args) throws IllegalAccessException {
        Field anEnum = JFields.getField(Class.class, "ENUM");
        JFields.setValue(anEnum, "modifiers", anEnum.getModifiers() & ~Modifier.FINAL);
        int old = anEnum.getInt(null);
        anEnum.setInt(null, 999);
        System.out.println(anEnum.get(null));
        Assertions.assertEquals(999, anEnum.get(null));
        anEnum.setInt(null, old);

        Object o = JMethods.invokeStatic(Unsafe.class, "getUnsafe");
        Assertions.assertNotNull(o);
        Object o1 = JFields.getStaticValue(Unsafe.class, "theUnsafe");
        Assertions.assertNotNull(o1);
        Object o2 = JFields.getStaticValue(Unsafe.class, "theInternalUnsafe");
        Assertions.assertNotNull(o2);
        Assertions.assertEquals("jdk.internal.misc.Unsafe", o2.getClass().getName());

        MethodHandle get = JMethods.getMethodHandle(Arrays.asList(1, 2, 3).getClass(), "get", int.class);
        Object invoke = JMethods.invoke(Arrays.asList(1, 2, 3), "get", 2);
        Assertions.assertEquals(3, invoke);

        long theUnsafe = JUnsafe.UNSAFE.staticFieldOffset(JFields.getField(Unsafe.class, "theUnsafe"));
        Object object = JUnsafe.UNSAFE.getObject(JUnsafe.UNSAFE.staticFieldBase(JFields.getField(Unsafe.class, "theUnsafe")), theUnsafe);
        Assertions.assertSame(object, JFields.getStaticValue(Unsafe.class, "theUnsafe"));

        Assertions.assertEquals("1\ttest", log.info("test"));
        JFields.setStaticValue(JUnsafeTest.class, "log", null);
        Assertions.assertThrows(NullPointerException.class, () -> log.info("test"));
        JFields.setStaticValue(JUnsafeTest.class, "log", new ALog(2));
        Assertions.assertEquals("2\ttest", log.info("test"));
    }

    private record ALog(int id) {
        public String info(String msg) {
            return id + "\t" + msg;
        }
    }
}