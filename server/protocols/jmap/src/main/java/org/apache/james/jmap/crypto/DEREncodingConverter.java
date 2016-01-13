package org.apache.james.jmap.crypto;

import org.bouncycastle.openssl.PEMReader;

import java.io.IOException;
import java.io.StringReader;
import java.security.interfaces.RSAPublicKey;
import java.util.Optional;

public class DEREncodingConverter {

    Optional<RSAPublicKey> fromPEM(Optional<String> pemKey) {

        return pemKey
                .map(k -> new PEMReader(new StringReader(k)))
                .map(r -> {
                    try {
                        return (RSAPublicKey) r.readObject();
                    } catch (IOException e) {
                        return null;
                    }
                });
    }
}