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

import org.apache.james.jmap.JMAPConfiguration;
import org.bouncycastle.openssl.PEMReader;

import javax.inject.Inject;
import java.io.IOException;
import java.io.StringReader;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Optional;

public class DERPublicKeyProvider {

    private final JMAPConfiguration config;

    @Inject
    public DERPublicKeyProvider(JMAPConfiguration config) {
        this.config = config;
    }

    public PublicKey get() {
        return fromPEMtoDER(config.getJwtPublicKeyPem())
                .orElseThrow(() -> new MissingOrInvalidKeyException());
    }

    private Optional<RSAPublicKey> fromPEMtoDER(Optional<String> pemKey) {

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

    public class MissingOrInvalidKeyException extends RuntimeException {}
}
