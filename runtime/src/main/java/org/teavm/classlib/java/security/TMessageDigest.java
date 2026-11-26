package org.teavm.classlib.java.security;

import java.io.ByteArrayOutputStream;

/** MD5, SHA-1, SHA-256 and SHA-512 through the app. */
public abstract class TMessageDigest {
    private final String algorithm;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    protected TMessageDigest(String algorithm) { this.algorithm = algorithm; }

    public static TMessageDigest getInstance(String algorithm) throws java.security.NoSuchAlgorithmException {
        String a = algorithm.toUpperCase().replace("-", "");
        if (!a.equals("MD5") && !a.equals("SHA1") && !a.equals("SHA256") && !a.equals("SHA512") && !a.equals("SHA384")) {
            throw new java.security.NoSuchAlgorithmException(algorithm);
        }
        return new TMessageDigest(a) {};
    }

    public final String getAlgorithm() { return algorithm; }
    public void update(byte input) { buffer.write(input); }
    public void update(byte[] input) { buffer.write(input, 0, input.length); }
    public void update(byte[] input, int offset, int len) { buffer.write(input, offset, len); }
    public void update(java.nio.ByteBuffer input) { while (input.hasRemaining()) buffer.write(input.get()); }
    public byte[] digest() {
        byte[] data = buffer.toByteArray();
        buffer.reset();
        if (algorithm.equals("MD5")) return exia.runtime.Md5.INSTANCE.digest(data);
        return exia.js.Host.crypto("digest", algorithm, null, null, data, null);
    }
    public byte[] digest(byte[] input) { update(input); return digest(); }
    public void reset() { buffer.reset(); }
    public final int getDigestLength() {
        switch (algorithm) { case "MD5": return 16; case "SHA1": return 20; case "SHA256": return 32; case "SHA384": return 48; default: return 64; }
    }
    public static boolean isEqual(byte[] a, byte[] b) { return java.util.Arrays.equals(a, b); }
}
