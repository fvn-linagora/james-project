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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;

import org.apache.james.jmap.crypto.JwtTokenVerifier;
import org.apache.james.jmap.exceptions.MailboxCreationException;
import org.apache.james.jmap.exceptions.NoAuthHeaderException;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;


public class JWTAuthenticationStrategyTest {

    private JWTAuthenticationStrategy testee;
    private MailboxManager mockedMailboxManager;
    private JwtTokenVerifier stubTokenManager;

    @Before
    public void setup() {
        mockedMailboxManager = mock(MailboxManager.class);

        stubTokenManager = mock(JwtTokenVerifier.class);

        testee = new JWTAuthenticationStrategy(stubTokenManager, mockedMailboxManager);
    }


    @Test
    public void createMailboxSessionShouldThrowWhenAuthHeaderIsEmpty() throws Exception {
        assertThatThrownBy(() -> testee.createMailboxSession(Stream.empty()))
            .isExactlyInstanceOf(NoAuthHeaderException.class);
    }

    @Test
    public void createMailboxSessionShouldReturnEmptyWhenAuthHeaderIsInvalid() throws Exception {
        assertThatThrownBy(() -> testee.createMailboxSession(Stream.of("bad")))
            .isExactlyInstanceOf(NoAuthHeaderException.class);
    }

    @Test
    public void createMailboxSessionShouldThrowWhenMailboxExceptionHasOccurred() throws Exception {
        String username = "username";
        String fakeAuthHeader = "blabla";
        String fakeAuthHeaderWithPrefix = JWTAuthenticationStrategy.AUTHORIZATION_HEADER_PREFIX + fakeAuthHeader;

        when(stubTokenManager.verify(fakeAuthHeader)).thenReturn(true);
        when(stubTokenManager.extractLogin(fakeAuthHeader)).thenReturn(username);
        when(mockedMailboxManager.createSystemSession(eq(username), any(Logger.class)))
                .thenThrow(new MailboxException());

        assertThatThrownBy(() -> testee.createMailboxSession(Stream.of(fakeAuthHeaderWithPrefix)))
                .isExactlyInstanceOf(MailboxCreationException.class);
    }

    @Test
    public void createMailboxSessionShouldReturnWhenAuthHeadersAreValid() throws Exception {
        String username = "123456789";
        String fakeAuthHeader = "blabla";
        String fakeAuthHeaderWithPrefix = JWTAuthenticationStrategy.AUTHORIZATION_HEADER_PREFIX + fakeAuthHeader;
        MailboxSession fakeMailboxSession = mock(MailboxSession.class);

        when(stubTokenManager.verify(fakeAuthHeader)).thenReturn(true);
        when(stubTokenManager.extractLogin(fakeAuthHeader)).thenReturn(username);
        when(mockedMailboxManager.createSystemSession(eq(username), any(Logger.class)))
                .thenReturn(fakeMailboxSession);

        MailboxSession result = testee.createMailboxSession(Stream.of(fakeAuthHeaderWithPrefix));
        assertThat(result).isEqualTo(fakeMailboxSession);
    }

    @Test
    public void checkAuthorizationHeaderShouldReturnFalsewWhenAuthHeaderIsEmpty() {
        assertThat(testee.checkAuthorizationHeader(Stream.empty())).isFalse();
    }

    @Test
    public void checkAuthorizationHeaderShouldReturnFalsewWhenAuthHeaderIsInvalid() {
        String fakeAuthHeader = "blabla";
        String fakeAuthHeaderWithPrefix = JWTAuthenticationStrategy.AUTHORIZATION_HEADER_PREFIX + fakeAuthHeader;

        when(stubTokenManager.verify(fakeAuthHeader)).thenReturn(false);

        assertThat(testee.checkAuthorizationHeader(Stream.of(fakeAuthHeaderWithPrefix))).isFalse();
    }

    @Test
    public void checkAuthorizationHeaderShouldReturnFalseWhenAuthHeadersAreInvalid() {
        String fakeAuthHeader = "blabla";
        String fakeAuthHeader2 = "blabla2";

        when(stubTokenManager.verify(fakeAuthHeader)).thenReturn(false);
        when(stubTokenManager.verify(fakeAuthHeader2)).thenReturn(false);

        Stream<String> authHeadersStream = Stream.of(fakeAuthHeader, fakeAuthHeader2)
                .map(h -> JWTAuthenticationStrategy.AUTHORIZATION_HEADER_PREFIX + h);
        assertThat(testee.checkAuthorizationHeader(authHeadersStream)).isFalse();
    }

    @Test
    public void checkAuthorizationHeaderShouldReturnTrueWhenAuthHeaderIsValid() {
        String validAuthHeader = "blabla";
        String validAuthHeaderWithPrefix = JWTAuthenticationStrategy.AUTHORIZATION_HEADER_PREFIX + validAuthHeader;

        when(stubTokenManager.verify(validAuthHeader)).thenReturn(true);

        assertThat(testee.checkAuthorizationHeader(Stream.of(validAuthHeaderWithPrefix))).isTrue();
    }

    @Test
    public void checkAuthorizationHeaderShouldReturnTrueWhenOneAuthHeaderIsValid() {
        String fakeAuthHeader = "blabla";
        String validAuthHeader = "blabla2";

        when(stubTokenManager.verify(fakeAuthHeader)).thenReturn(false);
        when(stubTokenManager.verify(validAuthHeader)).thenReturn(true);

        Stream<String> authHeadersStream = Stream.of(fakeAuthHeader, validAuthHeader)
                .map(h -> JWTAuthenticationStrategy.AUTHORIZATION_HEADER_PREFIX + h);
        assertThat(testee.checkAuthorizationHeader(authHeadersStream)).isTrue();
    }

}