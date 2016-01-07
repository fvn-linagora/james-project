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

import java.io.IOException;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import javax.inject.Inject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

import com.google.common.base.Throwables;

public class AuthenticationFilter implements Filter {

    public static final String MAILBOX_SESSION = "mailboxSession";
    private static final Logger LOG = Log.getLogger(AuthenticationFilter.class);

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

        Optional<String> authHeader = Optional.ofNullable(httpRequest.getHeader("Authorization"));

        LOG.info("AuthenticationFilter.doFilter crossed ...");

        // Bypass auth pipeline for request with method/verb OPTIONS
        boolean isAuthorized = "options".equals(httpRequest.getMethod().trim().toLowerCase());

        ListIterator<AuthenticationStrategy<Optional<String>>> authenticationMethodsIterator = authMethods.listIterator();
        while(!isAuthorized && authenticationMethodsIterator.hasNext())
        {
            AuthenticationStrategy<Optional<String>> authMethod = authenticationMethodsIterator.next();

            if (authMethod.checkAuthorizationHeader(authHeader)) {
                isAuthorized = true;
                LOG.debug("request was authorized via " + authMethod.getClass().getCanonicalName());

                addSessionToRequest(httpRequest, authHeader, h -> {
                    MailboxSession result = null;
                    try { result = authMethod.createMailboxSession(h); }
                    catch (MailboxException e) { Throwables.propagate(e); }
                    return result;
                });
            }
        }

        if (! isAuthorized) {
            httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        chain.doFilter(httpRequest, response);
    }

    private void addSessionToRequest(HttpServletRequest httpRequest, Optional<String> authHeader,
                                     Function<Optional<String>, MailboxSession> sessionCreator) {
        MailboxSession mailboxSession = sessionCreator.apply(authHeader);
        httpRequest.setAttribute(MAILBOX_SESSION, mailboxSession);
    }

    @Override
    public void destroy() {
    }

}
