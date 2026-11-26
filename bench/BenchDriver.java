import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.teavm.tooling.TeaVMTargetType;
import org.teavm.tooling.TeaVMTool;
import org.teavm.vm.TeaVMOptimizationLevel;

/** Translates Bench to JavaScript. Args: classpath outDir. */
public class BenchDriver {
    public static void main(String[] args) throws Exception {
        List<File> cp = new ArrayList<>();
        for (String p : args[0].split(":")) cp.add(new File(p));
        TeaVMTool tool = new TeaVMTool();
        tool.setTargetType(TeaVMTargetType.JAVASCRIPT);
        tool.setMainClass("Bench");
        tool.setTargetDirectory(new File(args[1]));
        tool.setTargetFileName("bench.js");
        tool.setClassPath(cp);
        java.net.URL[] urls = new java.net.URL[cp.size()];
        for (int k = 0; k < urls.length; k++) urls[k] = cp.get(k).toURI().toURL();
        tool.setClassLoader(new java.net.URLClassLoader(urls, BenchDriver.class.getClassLoader()));
        tool.setObfuscated(true);
        tool.setOptimizationLevel(TeaVMOptimizationLevel.ADVANCED);
        tool.setStrict(false);
        tool.generate();
        for (org.teavm.diagnostics.Problem p : tool.getProblemProvider().getSevereProblems()) {
            StringBuilder sb = new StringBuilder(p.getText());
            for (Object o : p.getParams()) sb.append(" | ").append(o);
            System.out.println(sb);
        }
    }
}
