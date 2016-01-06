package org.apache.james.jmap.crypto;

import com.google.common.base.Throwables;
import com.google.common.io.ByteStreams;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

public class DERPublicKeyProvider {
    public PublicKey get() {
        try {
            X509EncodedKeySpec spec = new X509EncodedKeySpec(getPublicKeyDer());
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePublic(spec);

        } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
            throw Throwables.propagate(e);
        }
    }

    private byte[] getPublicKeyDer() {
        try {
            InputStream publicKeyDerStream = ClassLoader.getSystemResourceAsStream("jwt-public.der");
            return ByteStreams.toByteArray(publicKeyDerStream);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }
}
