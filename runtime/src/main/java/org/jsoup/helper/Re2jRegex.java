package org.jsoup.helper;

/** Exia: jsoup without RE2/J; usingRe2j() is false, so this is never reached. */
final class Re2jRegex {
    private Re2jRegex() {}
    public static Regex compile(String regex) { return Regex.fromPattern(java.util.regex.Pattern.compile(regex)); }
}
