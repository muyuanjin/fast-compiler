package com.muyuanjin.compiler.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.muyuanjin.compiler.util.JFields;
import com.sun.tools.javac.file.BaseFileManager;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.util.Context;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.security.SecureClassLoader;
import java.util.*;
import java.util.jar.JarEntry;

import static javax.tools.StandardLocation.ANNOTATION_PROCESSOR_MODULE_PATH;

@Getter
@Setter
public class MemoryFileManager extends ForwardingJavaFileManager<JavaFileManager> {
    private static final Field CHARSET_FIELD = JFields.getField(BaseFileManager.class, "charset");

    private static final Cache<JavaFileObjectKey, String> BINARY_NAME_CACHE = Caffeine.newBuilder().softValues().build();

    private static final Cache<String, List<JavaFileObject>> FILE_LIST_CACHE = Caffeine.newBuilder().softValues().build();

    private static final Cache<ClassLoader, Cache<String, List<JavaFileObject>>> EXTERNAL_JARS_CACHE = Caffeine.newBuilder().weakKeys().build();


    //TODO impl classpath jdk.jshell.TaskFactory.addToClasspath
    private final List<MemoryOutputJavaFileObject> outputs = new ArrayList<>();
    private ClassLoader classLoader;

    @SneakyThrows
    public MemoryFileManager(JavacFileManager fileManager, ClassLoader classLoader) {
        super(fileManager);
        this.classLoader = classLoader;
    }

    public JavacFileManager getOriginal() {
        return (JavacFileManager) fileManager;
    }

    public void setContext(Context context) {
        getOriginal().setContext(context);
    }

    @SneakyThrows
    public void setCharset(Charset charset) {
        CHARSET_FIELD.set(getOriginal(), charset);
    }

    @Override
    public String inferBinaryName(Location location, final JavaFileObject file) {
        if (file instanceof BinaryJavaFileObject b) {
            String binaryName = b.getBinaryName();
            if (binaryName != null) {
                return binaryName;
            }
        }

        // 从缓存中获取或加载二进制名称
        return BINARY_NAME_CACHE.get(new JavaFileObjectKey(location.getName(), file.toString()),
                k -> super.inferBinaryName(location, file));
    }

    private record JavaFileObjectKey(String location, String file) {
    }

    @Override
    public ClassLoader getClassLoader(Location location) {
        return new SecureClassLoader() {
            @Override
            protected Class<?> findClass(String name) {
                for (MemoryOutputJavaFileObject output : outputs) {
                    if (output.getBinaryName().equals(name)) {
                        byte[] b = output.toByteArray();
                        return super.defineClass(name, b, 0, b.length);
                    }
                }
                return null;
            }
        };
    }

    /**
     * 关闭注解处理器
     * TODO 通过参数决定关闭与否
     */
    @Override
    public boolean hasLocation(Location location) {
        if (location == ANNOTATION_PROCESSOR_MODULE_PATH) {
            return true;
        }
        return super.hasLocation(location);
    }

    /**
     * 关闭注解处理器，通过空加载器减少扫描
     * TODO 通过参数决定关闭与否
     */
    @Override
    @SuppressWarnings("unchecked")
    public <S> ServiceLoader<S> getServiceLoader(Location location, Class<S> service) {
        // load EMPTY
        return (ServiceLoader<S>) ServiceLoader.loadInstalled(new Object() {}.getClass());
    }

    @Override
    public JavaFileObject getJavaFileForOutput(Location location, String name, JavaFileObject.Kind kind, FileObject source) throws IOException {
        if (kind == JavaFileObject.Kind.CLASS) {
            var fileObject = new MemoryOutputJavaFileObject(name);
            outputs.add(fileObject);
            return fileObject;
        }
        return super.getJavaFileForOutput(location, name, kind, source);
    }

    @Override
    public Iterable<JavaFileObject> list(Location location, String packageName, Set<JavaFileObject.Kind> kinds, boolean recurse) throws IOException {
        // 构造一个唯一的缓存键
        String key = location.getName() + ":" + packageName + ":" + kinds + ":" + recurse;
        // 从缓存中获取数据，如果缓存中没有则调用原始的list方法加载数据
        var fileManagerList = FILE_LIST_CACHE.get(key, k -> {
            Iterable<JavaFileObject> iterable = list0(location, packageName, kinds, recurse);
            List<JavaFileObject> result;
            if (iterable instanceof Collection<JavaFileObject> c) {
                result = new ArrayList<>(c);
            } else {
                result = new ArrayList<>();
                iterable.forEach(result::add);
            }
            return result;
        });
        if (location != StandardLocation.CLASS_PATH || packageName.startsWith("java.") || packageName.equals("java")) {
            return fileManagerList;
        }
        List<JavaFileObject> externalClasses = EXTERNAL_JARS_CACHE.get(classLoader, cl -> Caffeine.newBuilder().softValues().build())
                .get(packageName, this::findClassesInExternalJars);
        return externalClasses.isEmpty() ? fileManagerList : new AggregatingIterable<>(fileManagerList, externalClasses);
    }

    @SneakyThrows
    private Iterable<JavaFileObject> list0(Location location, String packageName, Set<JavaFileObject.Kind> kinds, boolean recurse) {
        return super.list(location, packageName, kinds, recurse);
    }

    // 当其他 jars 加载到外部类加载器中时，此解决方法是必要的，并且是对以下帖子中建议的解决方案的优化
    // http://atamur.blogspot.it/2009/10/using-built-in-javacompiler-with-custom.html
    private List<JavaFileObject> findClassesInExternalJars(String packageName) {
        try {
            Enumeration<URL> urlEnumeration = classLoader.getResources(packageName.replace('.', '/'));
            List<JavaFileObject> result = new ArrayList<>();
            while (urlEnumeration.hasMoreElements()) { //类路径上具有给定包的每个 jar 的一个 URL
                URL packageFolderURL = urlEnumeration.nextElement();
                if (!new File(packageFolderURL.getFile()).isDirectory()) {
                    List<JavaFileObject> classesInJar = processJar(packageFolderURL);
                    if (classesInJar != null) {
                        result.addAll(classesInJar);
                    }
                }
            }
            return result;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    private List<JavaFileObject> processJar(URL packageFolderURL) throws IOException {
        String jarUri = jarUri(packageFolderURL.toExternalForm());

        URLConnection urlConnection = packageFolderURL.openConnection();
        if (!(urlConnection instanceof JarURLConnection jarConn)) {
            return null;
        }

        String rootEntryName = jarConn.getEntryName();
        int rootEnd = rootEntryName.length() + 1;

        List<JavaFileObject> result = null;
        Enumeration<JarEntry> entryEnum = jarConn.getJarFile().entries();
        while (entryEnum.hasMoreElements()) {
            String name = entryEnum.nextElement().getName();
            if (name.startsWith(rootEntryName) && name.indexOf('/', rootEnd) == -1 && name.endsWith(".class")) {
                URI uri = URI.create(jarUri + "!/" + name);
                String binaryName = name.substring(0, name.length() - 6).replace('/', '.');
                if (result == null) {
                    result = new ArrayList<>();
                }
                result.add(new CustomJavaFileObject(binaryName, uri));
            }
        }
        return result;
    }

    public List<MemoryOutputJavaFileObject> getOutputs() {
        return Collections.unmodifiableList(outputs);
    }

    private static String convertResourceToClassName(final String pResourceName) {
        return stripExtension(pResourceName).replace('/', '.');
    }

    private static String stripExtension(final String pResourceName) {
        final int i = pResourceName.lastIndexOf('.');
        return pResourceName.substring(0, i);
    }

    private static String jarUri(String resourceUrl) {
        String jarUri = resourceUrl;
        int separator = jarUri.lastIndexOf('!');
        if (separator >= 0) {
            jarUri = jarUri.substring(0, separator);
        }
        return jarUri;
    }

    /**
     * 开启新一轮编译，清除输出
     */
    public void newRound() {
        outputs.clear();
    }

    /**
     * 替代close成为丢弃前的终结方法
     */
    @SneakyThrows
    public void doClose() {
        super.close();
    }

    @Override
    public void close() {
        // 重复使用，不使用原本的close接口
    }
}
