package org.teavm.classlib.java.security.spec;

public class TMGF1ParameterSpec implements TAlgorithmParameterSpec {
    public static final TMGF1ParameterSpec SHA1 = new TMGF1ParameterSpec("SHA-1");
    public static final TMGF1ParameterSpec SHA256 = new TMGF1ParameterSpec("SHA-256");
    private final String digest;
    public TMGF1ParameterSpec(String digest) { this.digest = digest; }
    public String getDigestAlgorithm() { return digest; }
}
