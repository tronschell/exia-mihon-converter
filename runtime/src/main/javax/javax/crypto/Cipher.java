package javax.crypto;

import exia.js.Host;
import java.io.ByteArrayOutputStream;
import java.security.Key;
import java.security.spec.AlgorithmParameterSpec;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;

/** javax.crypto.Cipher through the app's crypto (AES CBC/ECB/CTR/GCM, RSA OAEP/PKCS1). */
public class Cipher {
    public static final int ENCRYPT_MODE = 1;
    public static final int DECRYPT_MODE = 2;
    public static final int WRAP_MODE = 3;
    public static final int UNWRAP_MODE = 4;

    private final String transformation;
    private int mode;
    private byte[] key;
    private byte[] iv;
    private int tagBits = 128;
    private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
    private final ByteArrayOutputStream aad = new ByteArrayOutputStream();

    protected Cipher(String transformation) { this.transformation = transformation; }

    public static Cipher getInstance(String transformation) { return new Cipher(transformation); }
    public static Cipher getInstance(String transformation, String provider) { return new Cipher(transformation); }

    public final void init(int mode, Key key) { init(mode, key, (AlgorithmParameterSpec) null); }
    public final void init(int mode, Key key, java.security.SecureRandom random) { init(mode, key, (AlgorithmParameterSpec) null); }
    public final void init(int mode, Key key, AlgorithmParameterSpec params, java.security.SecureRandom random) { init(mode, key, params); }

    public final void init(int mode, Key key, AlgorithmParameterSpec params) {
        this.mode = mode;
        this.key = key.getEncoded();
        this.iv = null;
        if (params instanceof IvParameterSpec) iv = ((IvParameterSpec) params).getIV();
        if (params instanceof GCMParameterSpec) {
            iv = ((GCMParameterSpec) params).getIV();
            tagBits = ((GCMParameterSpec) params).getTLen();
        }
        pending.reset();
        aad.reset();
    }

    public final void updateAAD(byte[] src) { aad.write(src, 0, src.length); }

    public final byte[] update(byte[] input) {
        pending.write(input, 0, input.length);
        return new byte[0];
    }

    public final byte[] update(byte[] input, int offset, int len) {
        pending.write(input, offset, len);
        return new byte[0];
    }

    public final byte[] doFinal(byte[] input) {
        pending.write(input, 0, input.length);
        return doFinal();
    }

    public final byte[] doFinal(byte[] input, int offset, int len) {
        pending.write(input, offset, len);
        return doFinal();
    }

    public final byte[] doFinal() {
        byte[] data = pending.toByteArray();
        pending.reset();
        String extra = "{\"mode\":" + mode + ",\"tagBits\":" + tagBits + ",\"aad\":\"" + java.util.Base64.getEncoder().encodeToString(aad.toByteArray()) + "\"}";
        return Host.crypto("cipher", transformation, key, iv, data, extra);
    }

    public final int getBlockSize() { return transformation.startsWith("AES") ? 16 : 0; }
    public final byte[] getIV() { return iv; }
    public final String getAlgorithm() { return transformation; }
}
