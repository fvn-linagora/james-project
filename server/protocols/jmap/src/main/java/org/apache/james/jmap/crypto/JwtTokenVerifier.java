package org.apache.james.jmap.crypto;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureException;

import javax.inject.Inject;

public class JwtTokenVerifier {

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
