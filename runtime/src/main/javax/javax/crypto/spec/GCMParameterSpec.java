package javax.crypto.spec;

public class GCMParameterSpec implements java.security.spec.AlgorithmParameterSpec {
    private final int tLen;
    private final byte[] iv;
    public GCMParameterSpec(int tLen, byte[] src) { this.tLen = tLen; this.iv = src.clone(); }
    public GCMParameterSpec(int tLen, byte[] src, int offset, int len) { this.tLen = tLen; this.iv = java.util.Arrays.copyOfRange(src, offset, offset + len); }
    public int getTLen() { return tLen; }
    public byte[] getIV() { return iv.clone(); }
}
