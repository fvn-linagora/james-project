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

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

import com.google.common.collect.ImmutableList;
import org.apache.james.jmap.api.AccessTokenManager;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.jmap.api.access.AccessTokenRepository;
import org.apache.james.jmap.crypto.AccessTokenManagerImpl;
import org.apache.james.jmap.memory.access.MemoryAccessTokenRepository;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class AuthenticationFilterTest {
    private static final String TOKEN = "df991d2a-1c5a-4910-a90f-808b6eda133e";

    private HttpServletRequest mockedRequest;
    private HttpServletResponse mockedResponse;
    private AccessTokenManager accessTokenManager;
    private AccessTokenRepository accessTokenRepository;
    private AuthenticationFilter testee;
    private FilterChain filterChain;

    @Before
    public void setup() throws Exception {
        mockedRequest = mock(HttpServletRequest.class);
        mockedResponse = mock(HttpServletResponse.class);

        accessTokenRepository = new MemoryAccessTokenRepository(TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS));
        accessTokenManager = new AccessTokenManagerImpl(accessTokenRepository);

        when(mockedRequest.getMethod()).thenReturn("POST");
        List<AuthenticationStrategy<Optional<String>>> fakeAuthenticationStrategies = ImmutableList.of( new FakeAuthenticationStrategy(false));

        testee = new AuthenticationFilter(fakeAuthenticationStrategies);
        filterChain = mock(FilterChain.class);
    }

    @Test
    public void filterShouldReturnUnauthorizedOnNullAuthorizationHeader() throws Exception {
        when(mockedRequest.getHeader("Authorization"))
            .thenReturn(null);

        testee.doFilter(mockedRequest, mockedResponse, filterChain);

        verify(mockedResponse).sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }
    
    @Test
    public void filterShouldReturnUnauthorizedOnInvalidAuthorizationHeader() throws Exception {
        when(mockedRequest.getHeader("Authorization"))
            .thenReturn(TOKEN);

        testee.doFilter(mockedRequest, mockedResponse, filterChain);

        verify(mockedResponse).sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    public void filterShouldChainOnValidAuthorizationHeader() throws Exception {
        AccessToken token = AccessToken.fromString(TOKEN);
        when(mockedRequest.getHeader("Authorization"))
            .thenReturn(TOKEN);

        accessTokenRepository.addToken("user@domain.tld", token);

        AuthenticationFilter sut = new AuthenticationFilter(ImmutableList.of( new FakeAuthenticationStrategy(true)));
        sut.doFilter(mockedRequest, mockedResponse, filterChain);

        verify(filterChain).doFilter(any(ServletRequest.class), eq(mockedResponse));
    }

    @Test
    public void filterShouldReturnUnauthorizedOnBadAuthorizationHeader() throws Exception {
        when(mockedRequest.getHeader("Authorization"))
            .thenReturn("bad");

        testee.doFilter(mockedRequest, mockedResponse, filterChain);
        
        verify(mockedResponse).sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    public void shouldBypassAuthWhenRequestUseOptionsVerb() throws IOException, ServletException {
        HttpServletRequest stubbedRequest = mock(HttpServletRequest.class);
        when(stubbedRequest.getMethod()).thenReturn("OPTIONS");

        AuthenticationFilter sut = new AuthenticationFilter(new ArrayList<>());
        sut.doFilter(stubbedRequest, mockedResponse, filterChain);

        verify(filterChain).doFilter(any(ServletRequest.class), eq(mockedResponse));
    }

    private class FakeAuthenticationStrategy implements AuthenticationStrategy<Optional<String>> {

        private final boolean isAuthorized;

        private FakeAuthenticationStrategy(boolean isAuthorized) {
            this.isAuthorized = isAuthorized;
        }

        @Override
        public MailboxSession createMailboxSession(Optional<String> requestHeaders) throws MailboxException {
            return null;
        }

        @Override
        public boolean checkAuthorizationHeader(Optional<String> requestHeaders) {
            return isAuthorized;
        }
    }
}
