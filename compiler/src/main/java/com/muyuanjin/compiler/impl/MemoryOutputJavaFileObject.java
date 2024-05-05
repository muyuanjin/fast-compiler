package com.muyuanjin.compiler.impl;

import lombok.Getter;

import javax.tools.SimpleJavaFileObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;

@Getter
public class MemoryOutputJavaFileObject extends SimpleJavaFileObject implements BinaryJavaFileObject {
    private final ByteArrayOutputStream stream;
    private final String binaryName;

    public MemoryOutputJavaFileObject(String name) {
        super(URI.create(CompileUtil.toResourcePath("string:///", name, Kind.CLASS.extension)), Kind.CLASS);
        this.binaryName = name;
        this.stream = new ByteArrayOutputStream();
    }

    public byte[] toByteArray() {
        return stream.toByteArray();
    }

    @Override
    public InputStream openInputStream() {
        return new ByteArrayInputStream(toByteArray());
    }

    @Override
    public ByteArrayOutputStream openOutputStream() {
        return this.stream;
    }
}