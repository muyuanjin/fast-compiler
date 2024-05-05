package com.muyuanjin.compiler.impl;

import javax.tools.SimpleJavaFileObject;
import java.net.URI;
import java.net.URISyntaxException;

public class MemoryInputJavaFileObject extends SimpleJavaFileObject {
    private final String content;

    public MemoryInputJavaFileObject(String uri, String content) throws URISyntaxException {
        super(new URI("string:///" + uri), Kind.SOURCE);
        this.content = content;
    }

    /**
     * 获得类源码
     * 编译器编辑源码前，会通过此方法获取类的源码
     *
     * @param ignoreEncodingErrors 是否忽略编码错误
     * @return 需要编译的类的源码
     */
    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return content;
    }
}
