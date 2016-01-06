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
package org.apache.james.jmap;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class JMAPConfiguration {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        public String keystore;
        public String secret;
        public String jwtPublicKey;

        private Builder() {
        }

        public Builder keystore(String keystore) {
            this.keystore = keystore;
            return this;
        }

        public Builder secret(String secret) {
            this.secret = secret;
            return this;
        }

        public Builder jwtPublicKey(String jwtPublicKey) {
            this.jwtPublicKey = jwtPublicKey;
            return this;
        }

        public JMAPConfiguration build() {
            Preconditions.checkState(!Strings.isNullOrEmpty(keystore), "'keystore' is mandatory");
            Preconditions.checkState(!Strings.isNullOrEmpty(secret), "'secret' is mandatory");
            return new JMAPConfiguration(keystore, secret, jwtPublicKey);
        }
    }

    private final String keystore;
    private final String secret;
    private final String jwtPublicKey;

    @VisibleForTesting JMAPConfiguration(String keystore, String secret, String jwtPublicKey) {
        this.keystore = keystore;
        this.secret = secret;
        this.jwtPublicKey = jwtPublicKey;
    }

    public String getKeystore() {
        return keystore;
    }

    public String getSecret() {
        return secret;
    }

    public String getJwtPublicKey() {
        return jwtPublicKey;
    }
}
