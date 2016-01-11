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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.james.jmap.crypto.AccessTokenManagerImpl;
import org.apache.james.jmap.crypto.JwtTokenVerifier;
import org.apache.james.jmap.memory.access.MemoryAccessTokenRepository;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.exception.BadCredentialsException;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;
import java.util.concurrent.TimeUnit;


public class JWTAuthenticationStrategyTest {

    private MemoryAccessTokenRepository accessTokenRepository;
    private AccessTokenManagerImpl accessTokenManager;
    private JWTAuthenticationStrategy testee;

    @Before
    public void setup() {
        accessTokenRepository = new MemoryAccessTokenRepository(TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS));
        accessTokenManager = new AccessTokenManagerImpl(accessTokenRepository);
        MailboxManager mockedMailboxManager = mock(MailboxManager.class);

        JwtTokenVerifier tokenManager = mock(JwtTokenVerifier.class);
        when(tokenManager.extractLogin("token")).thenReturn("1234567899");
        testee = new JWTAuthenticationStrategy(tokenManager, mockedMailboxManager);
    }

    @Test(expected=BadCredentialsException.class)
    public void createMailboxSessionShouldThrowWhenAuthHeaderIsEmpty() throws Exception {
        testee.createMailboxSession(Optional.empty());
    }

    @Test(expected=BadCredentialsException.class)
    public void createMailboxSessionShouldThrowWhenAuthHeaderIsInvalid() throws Exception {
        testee.createMailboxSession(Optional.of("bad"));
    }
}