package org.teavm.classlib.java.text;

import java.util.Comparator;
import java.util.Locale;

/** Collator on the app's locale-aware comparison (JavaScript's localeCompare). */
public class TCollator implements Comparator<Object>, Cloneable {
    public static final int PRIMARY = 0;
    public static final int SECONDARY = 1;
    public static final int TERTIARY = 2;
    public static final int IDENTICAL = 3;
    public static final int NO_DECOMPOSITION = 0;
    public static final int CANONICAL_DECOMPOSITION = 1;
    public static final int FULL_DECOMPOSITION = 2;
    private int strength = TERTIARY;

    protected TCollator() {}
    public static TCollator getInstance() { return new TCollator(); }
    public static TCollator getInstance(Locale locale) { return new TCollator(); }
    public int compare(String a, String b) { return exia.js.Text.compare(a, b, strength); }
    @Override public int compare(Object a, Object b) { return compare(a.toString(), b.toString()); }
    public boolean equals(String a, String b) { return compare(a, b) == 0; }
    public int getStrength() { return strength; }
    public void setStrength(int strength) { this.strength = strength; }
    public int getDecomposition() { return NO_DECOMPOSITION; }
    public void setDecomposition(int d) {}
    @Override public Object clone() { TCollator c = new TCollator(); c.strength = strength; return c; }
}
