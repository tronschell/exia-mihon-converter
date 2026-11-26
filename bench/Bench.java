import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.teavm.jso.JSExport;

/**
 * What an extension does with a page, timed: parse the HTML, select the title cards, read links, titles and images.
 * Runs on the JVM (java Bench page.html...) and, translated by TeaVM, in JavaScriptCore (bench.mjs).
 */
public class Bench {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) return;
        int n = Integer.parseInt(System.getProperty("n", "20"));
        for (String path : args) {
            String html = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)), "UTF-8");
            run(html, 3);
            System.out.println(path + "\t" + run(html, n));
        }
    }

    /** Milliseconds per page (average of n), after the caller's warm-up. */
    @JSExport
    public static String run(String html, int n) {
        long t0 = System.nanoTime();
        int found = 0;
        for (int i = 0; i < n; i++) {
            Document doc = Jsoup.parse(html, "https://example.com/");
            for (Element a : doc.select("a[href]")) {
                found += a.absUrl("href").length() + a.text().length();
            }
            for (Element img : doc.select("img")) found += img.attr("abs:src").length();
        }
        double ms = (System.nanoTime() - t0) / 1e6 / n;
        return String.format("%.2f", ms) + "\t" + found / n;
    }
}
