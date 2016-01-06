package org.apache.james.jmap.crypto;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureException;

import javax.inject.Inject;

public class JwtTokenVerifier {

    private static final String pubKey = "-----BEGIN PUBLIC KEY-----\n" +
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtlChO/nlVP27MpdkG0Bh\n" +
            "16XrMRf6M4NeyGa7j5+1UKm42IKUf3lM28oe82MqIIRyvskPc11NuzSor8HmvH8H\n" +
            "lhDs5DyJtx2qp35AT0zCqfwlaDnlDc/QDlZv1CoRZGpQk1Inyh6SbZwYpxxwh0fi\n" +
            "+d/4RpE3LBVo8wgOaXPylOlHxsDizfkL8QwXItyakBfMO6jWQRrj7/9WDhGf4Hi+\n" +
            "GQur1tPGZDl9mvCoRHjFrD5M/yypIPlfMGWFVEvV5jClNMLAQ9bYFuOc7H1fEWw6\n" +
            "U1LZUUbJW9/CH45YXz82CYqkrfbnQxqRb2iVbVjs/sHopHd1NTiCfUtwvcYJiBVj\n" +
            "kwIDAQAB\n" +
            "-----END PUBLIC KEY-----";

    private final DERPublicKeyProvider pubKeyProvider;

    @Inject
    public JwtTokenVerifier(DERPublicKeyProvider pubKeyProvider) {
        this.pubKeyProvider = pubKeyProvider;
    }

    public boolean verify(String token) {
        try {
            Jwts.parser().setSigningKey(pubKeyProvider.get()).parseClaimsJws(token);
            return true;

        } catch (SignatureException e) {
            return false;
        }
    }


    public String extractLogin(String token) {
        return Jwts.parser().setSigningKey(pubKeyProvider.get()).parseClaimsJws(token).getBody().getSubject();
    }
}
