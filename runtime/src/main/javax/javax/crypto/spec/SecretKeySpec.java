package javax.crypto.spec;

public class SecretKeySpec implements java.security.spec.KeySpec, javax.crypto.SecretKey {
    private final byte[] key;
    private final String algorithm;
    public SecretKeySpec(byte[] key, String algorithm) { this.key = key.clone(); this.algorithm = algorithm; }
    public SecretKeySpec(byte[] key, int offset, int len, String algorithm) { this.key = java.util.Arrays.copyOfRange(key, offset, offset + len); this.algorithm = algorithm; }
    @Override public String getAlgorithm() { return algorithm; }
    @Override public String getFormat() { return "RAW"; }
    @Override public byte[] getEncoded() { return key.clone(); }
}
