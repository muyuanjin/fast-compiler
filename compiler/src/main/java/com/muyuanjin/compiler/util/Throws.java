package com.muyuanjin.compiler.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Throws {
    @SuppressWarnings("unchecked")
    public static <T extends Throwable> RuntimeException sneakyThrows(Throwable throwable) throws T {
        throw (T) throwable;
    }
}