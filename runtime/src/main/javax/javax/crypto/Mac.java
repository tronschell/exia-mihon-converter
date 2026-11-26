package javax.crypto;

import exia.js.Host;
import java.io.ByteArrayOutputStream;
import java.security.Key;

public class Mac {
    private final String algorithm;
    private byte[] key;
    private final ByteArrayOutputStream data = new ByteArrayOutputStream();

    protected Mac(String algorithm) { this.algorithm = algorithm; }
    public static Mac getInstance(String algorithm) { return new Mac(algorithm); }
    public final void init(Key key) { this.key = key.getEncoded(); data.reset(); }
    public final void init(Key key, java.security.spec.AlgorithmParameterSpec params) { init(key); }
    public final void update(byte input) { data.write(input); }
    public final void update(byte[] input) { data.write(input, 0, input.length); }
    public final void update(byte[] input, int offset, int len) { data.write(input, offset, len); }
    public final byte[] doFinal() {
        byte[] out = Host.crypto("hmac", algorithm, key, null, data.toByteArray(), null);
        data.reset();
        return out;
    }
    public final byte[] doFinal(byte[] input) { update(input); return doFinal(); }
    public final int getMacLength() {
        String a = algorithm.toUpperCase();
        return a.contains("512") ? 64 : a.contains("384") ? 48 : a.contains("256") ? 32 : a.contains("MD5") ? 16 : 20;
    }
    public final String getAlgorithm() { return algorithm; }
    public final void reset() { data.reset(); }
}
