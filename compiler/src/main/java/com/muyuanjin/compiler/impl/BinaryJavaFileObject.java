package com.muyuanjin.compiler.impl;

import javax.tools.JavaFileObject;

interface BinaryJavaFileObject extends JavaFileObject {
    String getBinaryName();
}
