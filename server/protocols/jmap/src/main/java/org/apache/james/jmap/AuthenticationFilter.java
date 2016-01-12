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
import org.apache.james.mailbox.MailboxSession;

import javax.inject.Inject;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class AuthenticationFilter implements Filter {

    public static final String MAILBOX_SESSION = "mailboxSession";
    public static final String AUTHORIZATION_HEADERS = "Authorization";

    private final List<AuthenticationStrategy> authMethods;

    @Inject
    @VisibleForTesting
    AuthenticationFilter(List<AuthenticationStrategy> authMethods) {
        this.authMethods = authMethods;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Bypass auth pipeline for request with method/verb OPTIONS
        boolean isCORSPreflightRequest = "options".equals(httpRequest.getMethod().trim().toLowerCase());

        Optional<HttpServletRequest> requestWithSession = authMethods.stream()
                .filter(auth -> auth.checkAuthorizationHeader(getAuthHeaders(httpRequest)))
                .findFirst()
                .map(auth -> addSessionToRequest(httpRequest, createSession(auth, getAuthHeaders(httpRequest))));

        if (!isCORSPreflightRequest && !requestWithSession.isPresent()) {
            httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        chain.doFilter(httpRequest, response);
    }

    private Stream<String> getAuthHeaders(HttpServletRequest httpRequest) {
        Enumeration<String> authHeadersIterator = httpRequest.getHeaders(AUTHORIZATION_HEADERS);

        return authHeadersIterator != null && authHeadersIterator.hasMoreElements() ? Collections.list(authHeadersIterator).stream() : Stream.of();
    }

    private HttpServletRequest addSessionToRequest(HttpServletRequest httpRequest, Optional<MailboxSession> mailboxSession) {
        if (mailboxSession.isPresent()) {

            httpRequest.setAttribute(MAILBOX_SESSION, mailboxSession.get());
        }
        return httpRequest;
    }

    private Optional<MailboxSession> createSession(AuthenticationStrategy authenticationMethod,
                                                   Stream<String> authorizationHeaders) {

        return authenticationMethod.createMailboxSession(authorizationHeaders);
    }

    @Override
    public void destroy() {
    }

}
