/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/
package org.apache.james.jmap.crypto;

import com.google.common.base.Throwables;
import com.google.common.io.ByteStreams;
import org.apache.james.jmap.JMAPConfiguration;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

public class DERPublicKeyProvider {

    private final JMAPConfiguration config;
    private static final byte[] MISSING_KEY = {};

    @Inject
    public DERPublicKeyProvider(JMAPConfiguration config) {
        this.config = config;
    }

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
            return getDERKey();
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private byte[] getDERKey() throws IOException {
        return config.getJwtPublicKeyPem()
                .map(k -> k.getBytes())
                .orElse(MISSING_KEY);
    }
}
