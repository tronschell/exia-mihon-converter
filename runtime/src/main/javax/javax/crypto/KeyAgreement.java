package javax.crypto;

public class KeyAgreement {
    public static KeyAgreement getInstance(String algorithm) { throw new UnsupportedOperationException("KeyAgreement " + algorithm); }
    public final void init(java.security.Key key) {}
    public final java.security.Key doPhase(java.security.Key key, boolean lastPhase) { return null; }
    public final byte[] generateSecret() { return new byte[0]; }
}
