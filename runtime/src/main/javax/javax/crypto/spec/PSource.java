package javax.crypto.spec;

public class PSource {
    private final String alg;
    protected PSource(String alg) { this.alg = alg; }
    public String getAlgorithm() { return alg; }
    public static final class PSpecified extends PSource {
        public static final PSpecified DEFAULT = new PSpecified(new byte[0]);
        public PSpecified(byte[] p) { super("PSpecified"); }
    }
}
