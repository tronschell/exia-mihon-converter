import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import javax.tools.*;
import org.objectweb.asm.*;
import org.teavm.tooling.*;
import org.teavm.diagnostics.*;
import org.teavm.vm.TeaVMOptimizationLevel;

/** Translates Tachiyomi extension jars to JavaScript with TeaVM. Args: runtimeCp outDir jar... */
public class Driver {
    public static void main(String[] args) throws Exception {
        List<File> runtime = new ArrayList<>();
        for (String p : args[0].split(":")) runtime.add(new File(p));
        File out = new File(args[1]);
        out.mkdirs();
        boolean obf = !"0".equals(System.getenv("OBF"));
        for (int i = 2; i < args.length; i++) {
            File jar = new File(args[i]);
            String name = jar.getName().replace(".jar", "");
            long t0 = System.currentTimeMillis();
            String status;
            List<String> problems = new ArrayList<>();
            try {
                File factoryDir = Files.createTempDirectory("factory").toFile();
                writeFactory(jar, runtime, factoryDir);
                List<String> assets = new ArrayList<>();
                try (ZipFile z = new ZipFile(jar)) {
                    for (Enumeration<? extends ZipEntry> e = z.entries(); e.hasMoreElements(); ) {
                        String n = e.nextElement().getName();
                        if (n.startsWith("assets/") && !n.endsWith("/")) assets.add(n);
                    }
                }
                AssetsSupplier.current = assets.toArray(new String[0]);
                List<File> cp = new ArrayList<>();
                cp.add(factoryDir);
                cp.addAll(runtime);
                cp.add(rewrite(jar));
                TeaVMTool tool = new TeaVMTool();
                tool.setTargetType(TeaVMTargetType.JAVASCRIPT);
                tool.setMainClass("exia.js.Main");
                tool.setTargetDirectory(out);
                tool.setTargetFileName(name + ".js");
                tool.setClassPath(cp);
                java.net.URL[] urls = new java.net.URL[cp.size()];
                for (int k = 0; k < urls.length; k++) urls[k] = cp.get(k).toURI().toURL();
                tool.setClassLoader(new java.net.URLClassLoader(urls, Driver.class.getClassLoader()));
                tool.setObfuscated(obf);
                tool.setOptimizationLevel(TeaVMOptimizationLevel.ADVANCED);
                tool.setStrict(false);
                tool.setAsyncEnabled(true);
                tool.setLog(new TeaVMToolLog() {
                    public void info(String t) {} public void debug(String t) {} public void warning(String t) {}
                    public void error(String t) { problems.add(t); }
                    public void info(String t, Throwable e) {} public void debug(String t, Throwable e) {} public void warning(String t, Throwable e) {}
                    public void error(String t, Throwable e) { problems.add(t + " " + e); }
                });
                tool.generate();
                ProblemProvider pp = tool.getProblemProvider();
                for (Problem p : pp.getSevereProblems()) problems.add(describe(p));
                status = pp.getSevereProblems().isEmpty() ? "ok" : "problems";
            } catch (Throwable e) {
                status = "crash";
                problems.add(e.toString());
            }
            File js = new File(out, name + ".js");
            System.out.println("RESULT\t" + name + "\t" + status + "\t" + (js.exists() ? js.length() : 0) + "\t" + (System.currentTimeMillis() - t0) + "ms");
            try (PrintWriter w = new PrintWriter(new File(out, name + ".problems.txt"))) { for (String p : problems) w.println(p); }
        }
    }

    /**
     * Copies the jar, sending Class.getResource calls to exia.js.Res (TeaVM's Class has no getResource). Writes a jar,
     * not a folder: obfuscated names like a.class and A.class collide on case-insensitive disks (macOS).
     */
    static File rewrite(File jar) throws IOException {
        File outJar = File.createTempFile("ext", ".jar");
        try (ZipFile z = new ZipFile(jar); ZipOutputStream zo = new ZipOutputStream(new FileOutputStream(outJar))) {
            for (Enumeration<? extends ZipEntry> e = z.entries(); e.hasMoreElements(); ) {
                ZipEntry en = e.nextElement();
                if (en.isDirectory()) continue;
                byte[] bytes = z.getInputStream(en).readAllBytes();
                if (en.getName().endsWith(".class")) {
                    ClassReader cr = new ClassReader(bytes);
                    ClassWriter cw = new ClassWriter(0);
                    cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
                        public MethodVisitor visitMethod(int a, String n, String d, String sig, String[] ex) {
                            return new MethodVisitor(Opcodes.ASM9, super.visitMethod(a, n, d, sig, ex)) {
                                public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
                                    if (op == Opcodes.INVOKEVIRTUAL && owner.equals("java/lang/Class") && name.equals("getResource")) {
                                        super.visitMethodInsn(Opcodes.INVOKESTATIC, "exia/js/Res", "getResource",
                                            "(Ljava/lang/Class;Ljava/lang/String;)Ljava/net/URL;", false);
                                    } else super.visitMethodInsn(op, owner, name, desc, itf);
                                }
                            };
                        }
                    }, 0);
                    bytes = cw.toByteArray();
                }
                zo.putNextEntry(new ZipEntry(en.getName()));
                zo.write(bytes);
                zo.closeEntry();
            }
        }
        outJar.deleteOnExit();
        return outJar;
    }

    static String describe(Problem p) {
        StringBuilder sb = new StringBuilder(p.getText());
        for (Object o : p.getParams()) sb.append(" | ").append(o);
        if (p.getLocation() != null && p.getLocation().getMethod() != null) sb.append(" @ ").append(p.getLocation().getMethod());
        return sb.toString();
    }

    /** Factory: creates keiyoushi.source.Generated and records the type argument of each FullTypeReference subclass. */
    static void writeFactory(File jar, List<File> runtime, File dir) throws Exception {
        Map<String, String> types = new TreeMap<>();
        boolean[] usesProtoBuf = { false };
        try (ZipFile z = new ZipFile(jar)) {
            for (Enumeration<? extends ZipEntry> e = z.entries(); e.hasMoreElements(); ) {
                ZipEntry en = e.nextElement();
                if (!en.getName().endsWith(".class")) continue;
                byte[] classBytes = z.getInputStream(en).readAllBytes();
                if (new String(classBytes, java.nio.charset.StandardCharsets.ISO_8859_1).contains("kotlinx/serialization/protobuf/ProtoBuf")) usesProtoBuf[0] = true;
                ClassReader cr = new ClassReader(classBytes);
                cr.accept(new ClassVisitor(Opcodes.ASM9) {
                    public void visit(int v, int a, String name, String sig, String sup, String[] itf) {
                        if ("uy/kohesive/injekt/api/FullTypeReference".equals(sup) && sig != null) {
                            int s = sig.indexOf('<'), t = sig.indexOf(';', s);
                            String arg = sig.substring(s + 2, t).replace('/', '.');
                            int g = arg.indexOf('<');
                            types.put(name.replace('/', '.'), g >= 0 ? arg.substring(0, g) : arg);
                        }
                    }
                }, ClassReader.SKIP_CODE);
            }
        }
        StringBuilder src = new StringBuilder("package exia.js;\nimport java.util.*;\npublic final class Factory {\n  public static final Map<String,String> types = new HashMap<>();\n  static {\n");
        for (Map.Entry<String, String> t : types.entrySet()) src.append("    types.put(\"").append(t.getKey()).append("\", \"").append(t.getValue()).append("\");\n");
        src.append("  }\n  public static Object create() { return new keiyoushi.source.Generated(); }\n");
        src.append("  public static Object provide(String type) {\n");
        if (usesProtoBuf[0]) src.append("    if (type.equals(\"kotlinx.serialization.protobuf.ProtoBuf\")) return kotlinx.serialization.protobuf.ProtoBuf.Default;\n");
        src.append("    return null;\n  }\n}\n");
        File srcFile = new File(dir, "Factory.java");
        Files.writeString(srcFile.toPath(), src);
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        StringBuilder cp = new StringBuilder(jar.getPath());
        for (File f : runtime) cp.append(':').append(f.getPath());
        int rc = javac.run(null, null, null, "-nowarn", "-proc:none", "--release", "11", "-cp", cp.toString(), "-d", dir.getPath(), srcFile.getPath());
        if (rc != 0) throw new IllegalStateException("factory compile failed");
    }
}
