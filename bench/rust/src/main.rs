// The same work as Bench.java in native code (html5ever through scraper): parse, select links and images,
// resolve URLs, read text. Prints milliseconds per page. Usage: html-bench page.html...
use scraper::{Html, Selector};
use std::time::Instant;
use url::Url;

fn run(html: &str, n: usize) -> (f64, usize) {
    let base = Url::parse("https://example.com/").unwrap();
    let links = Selector::parse("a[href]").unwrap();
    let images = Selector::parse("img").unwrap();
    let t0 = Instant::now();
    let mut found = 0;
    for _ in 0..n {
        let doc = Html::parse_document(html);
        for a in doc.select(&links) {
            let href = a.value().attr("href").unwrap_or("");
            found += base.join(href).map(|u| u.as_str().len()).unwrap_or(0);
            found += a.text().map(|t| t.trim().len()).sum::<usize>();
        }
        for img in doc.select(&images) {
            let src = img.value().attr("src").unwrap_or("");
            found += base.join(src).map(|u| u.as_str().len()).unwrap_or(0);
        }
    }
    (t0.elapsed().as_secs_f64() * 1000.0 / n as f64, found / n)
}

fn main() {
    let n: usize = std::env::var("N").ok().and_then(|v| v.parse().ok()).unwrap_or(20);
    for path in std::env::args().skip(1) {
        let html = std::fs::read_to_string(&path).unwrap();
        run(&html, 3);
        let (ms, found) = run(&html, n);
        println!("{}\t{:.2}\t{}", path, ms, found);
    }
}
