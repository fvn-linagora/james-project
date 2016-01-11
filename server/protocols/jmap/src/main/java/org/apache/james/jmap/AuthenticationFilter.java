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

import org.apache.james.mailbox.MailboxSession;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

import javax.inject.Inject;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

public class AuthenticationFilter implements Filter {

    public static final String MAILBOX_SESSION = "mailboxSession";
    private static final Logger LOG = Log.getLogger(AuthenticationFilter.class);
    public static final String AUTHORIZATION_HEADERS = "Authorization";

    private final List<AuthenticationStrategy<Optional<String>>> authMethods;

    @Inject
    public AuthenticationFilter(List<AuthenticationStrategy<Optional<String>>> authMethods) {
        this.authMethods = authMethods;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        Optional<String> authHeader = Optional.ofNullable(httpRequest.getHeader(AUTHORIZATION_HEADERS));

        Stream<String> authHeaders = StreamHelpers.enumerationAsStream(httpRequest.getHeaders(AUTHORIZATION_HEADERS));

        // Bypass auth pipeline for request with method/verb OPTIONS
        boolean isAuthorized = "options".equals(httpRequest.getMethod().trim().toLowerCase());

        ListIterator<AuthenticationStrategy<Optional<String>>> authenticationMethodsIterator = authMethods.listIterator();
        AuthenticationStrategy<Optional<String>> authMethod = noAuthentication();
        while(!isAuthorized && authenticationMethodsIterator.hasNext())
        {
            authMethod = authenticationMethodsIterator.next();

            if (authMethod.checkAuthorizationHeader(authHeader)) {
                isAuthorized = true;
                LOG.debug("request was authorized via " + authMethod.getClass().getCanonicalName());
            }
        }

        Optional<HttpServletRequest> requestWithSession = authMethods.stream()
                .filter(auth -> auth.checkAuthorizationHeader(authHeader))
                .findFirst()
                .map(auth -> addSessionToRequest(httpRequest, createSession(auth, authHeader)));

        if (! isAuthorized || ! requestWithSession.isPresent()) {
            httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        chain.doFilter(httpRequest, response);
    }

    private HttpServletRequest addSessionToRequest(HttpServletRequest httpRequest, Optional<MailboxSession> mailboxSession) {
        if (mailboxSession.isPresent()) {

            httpRequest.setAttribute(MAILBOX_SESSION, mailboxSession);
        }
        return httpRequest;
    }

    private Optional<MailboxSession> createSession(AuthenticationStrategy<Optional<String>> authenticationMethod,
                                                   Optional<String> authorizationHeader) {

        return authenticationMethod.createMailboxSession(authorizationHeader);
    }

    private AuthenticationStrategy<Optional<String>> noAuthentication() {
        return new AuthenticationStrategy<Optional<String>>() {
            @Override
            public Optional<MailboxSession> createMailboxSession(Optional<String> requestHeaders) {
                return Optional.empty();
            }

            @Override
            public boolean checkAuthorizationHeader(Optional<String> requestHeaders) {
                return false;
            }
        };
    }

    @Override
    public void destroy() {
    }

}
