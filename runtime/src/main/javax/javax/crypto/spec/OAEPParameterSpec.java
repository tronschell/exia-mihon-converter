package javax.crypto.spec;

public class OAEPParameterSpec implements java.security.spec.AlgorithmParameterSpec {
    private final String md;
    public OAEPParameterSpec(String mdName, String mgfName, java.security.spec.AlgorithmParameterSpec mgfSpec, PSource pSrc) { md = mdName; }
    public String getDigestAlgorithm() { return md; }
}
