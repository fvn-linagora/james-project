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

package org.apache.james.modules;

import java.io.FileNotFoundException;
import java.util.Optional;

import javax.inject.Singleton;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.jmap.JMAPConfiguration;
import org.apache.james.jmap.PortConfiguration;
import org.apache.james.jmap.methods.GetMessageListMethod;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Names;

public class TestJMAPServerModule extends AbstractModule{

    private final int maximumLimit;

    public TestJMAPServerModule(int maximumLimit) {
        this.maximumLimit = maximumLimit;
    }

    @Override
    protected void configure() {
        bind(PortConfiguration.class).to(RandomPortConfiguration.class).in(Singleton.class);
        bindConstant().annotatedWith(Names.named(GetMessageListMethod.MAXIMUM_LIMIT)).to(maximumLimit);
    }

    @Provides
    @Singleton
    JMAPConfiguration provideConfiguration() throws FileNotFoundException, ConfigurationException{
        return JMAPConfiguration.builder()
                .keystore("keystore")
                .secret("james72laBalle")
                .jwtPublicKeyPem("publicKey")
                .build();
    }
    
    private static class RandomPortConfiguration implements PortConfiguration {

        @Override
        public Optional<Integer> getPort() {
            return Optional.empty();
        }
    }
}
