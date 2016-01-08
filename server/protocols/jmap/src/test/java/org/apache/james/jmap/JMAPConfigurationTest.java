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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.Test;

public class JMAPConfigurationTest {

    @Test
    public void buildShouldThrowWhenKeystoreIsNull() {
        assertThatThrownBy(() -> JMAPConfiguration.builder()
                .keystore(null)
                .build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("'keystore' is mandatory");
    }

    @Test
    public void buildShouldThrowWhenKeystoreIsEmpty() {
        assertThatThrownBy(() -> JMAPConfiguration.builder()
                .keystore("")
                .build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("'keystore' is mandatory");
    }

    @Test
    public void buildShouldThrowWhenSecretIsNull() {
        assertThatThrownBy(() -> JMAPConfiguration.builder()
                .keystore("keystore")
                .secret(null)
                .build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("'secret' is mandatory");
    }

    @Test
    public void buildShouldThrowWhenSecretIsEmpty() {
        assertThatThrownBy(() -> JMAPConfiguration.builder()
                .keystore("keystore")
                .secret("")
                .build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("'secret' is mandatory");
    }

    @Test
    public void buildShouldThrowWhenJwtPublicKeyPemIsNull() {
        assertThatThrownBy(() -> JMAPConfiguration.builder()
                .keystore("keystore")
                .secret("secret")
                .jwtPublicKeyPem(null)
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void buildShouldWork() {
        JMAPConfiguration expectedJMAPConfiguration = new JMAPConfiguration("keystore", "secret", Optional.of("file://conf/jwt_publickey"));

        JMAPConfiguration jmapConfiguration = JMAPConfiguration.builder()
            .keystore("keystore")
            .secret("secret")
            .jwtPublicKeyPem(Optional.of("file://conf/jwt_publickey"))
            .build();
        assertThat(jmapConfiguration).isEqualToComparingFieldByField(expectedJMAPConfiguration);
    }
}
