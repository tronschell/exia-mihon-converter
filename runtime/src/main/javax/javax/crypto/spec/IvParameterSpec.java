package javax.crypto.spec;

public class IvParameterSpec implements java.security.spec.AlgorithmParameterSpec {
    private final byte[] iv;
    public IvParameterSpec(byte[] iv) { this.iv = iv.clone(); }
    public IvParameterSpec(byte[] iv, int offset, int len) { this.iv = java.util.Arrays.copyOfRange(iv, offset, offset + len); }
    public byte[] getIV() { return iv.clone(); }
}
