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
