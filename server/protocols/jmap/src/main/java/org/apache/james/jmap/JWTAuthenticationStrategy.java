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

import com.google.common.base.Throwables;
import org.apache.james.jmap.crypto.JwtTokenVerifier;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.BadCredentialsException;
import org.apache.james.mailbox.exception.MailboxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Optional;
import java.util.stream.Stream;

public class JWTAuthenticationStrategy implements AuthenticationStrategy<Stream<String>> {

    private static final Logger LOG = LoggerFactory.getLogger(JWTAuthenticationStrategy.class);
    public static final String AUTHORIZATION_HEADER_PREFIX = "Bearer ";
    private final JwtTokenVerifier tokenManager;
    private final MailboxManager mailboxManager;

    @Inject
    public JWTAuthenticationStrategy(JwtTokenVerifier tokenManager, MailboxManager mailboxManager) {
        this.tokenManager = tokenManager;
        this.mailboxManager = mailboxManager;
    }

    @Override
    public Optional<MailboxSession> createMailboxSession(Stream<String> authHeaders) {

        Stream<String> extractedTokens = authHeaders
                // extract tokens
                .filter(h -> h.startsWith(AUTHORIZATION_HEADER_PREFIX))
                .map(h -> h.substring(AUTHORIZATION_HEADER_PREFIX.length()));

        Stream<String> extractedLogins = extractedTokens
                // keep valid ones
                .filter(tokenManager::verify)
                // get user login
                .map(tokenManager::extractLogin);

        return extractedLogins
                .map(l -> {
                    try {
                        return mailboxManager.createSystemSession(l, LOG);
                    } catch (MailboxException e) {
                        Throwables.propagate(e);
                        return null;
                    }
                })
                .findFirst();
    }

    @Override
    public boolean checkAuthorizationHeader(Stream<String> authHeaders) {
        return authHeaders
                // extract token
                .filter(h -> h.startsWith(AUTHORIZATION_HEADER_PREFIX))
                .map(h -> h.substring(AUTHORIZATION_HEADER_PREFIX.length()))
                // verify token
                .anyMatch(tokenManager::verify);
    }
}
