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

import org.apache.james.jmap.api.AccessTokenManager;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.jmap.api.access.exceptions.NotAnUUIDException;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Optional;
import java.util.stream.Stream;

public class AccessTokenAuthenticationStrategy implements AuthenticationStrategy<Stream<String>> {

    private static final Logger LOG = LoggerFactory.getLogger(AccessTokenAuthenticationStrategy.class);

    private final AccessTokenManager accessTokenManager;
    private final MailboxManager mailboxManager;

    @Inject
    public AccessTokenAuthenticationStrategy(AccessTokenManager accessTokenManager, MailboxManager mailboxManager) {
        this.accessTokenManager = accessTokenManager;
        this.mailboxManager = mailboxManager;
    }

    @Override
    public Optional<MailboxSession> createMailboxSession(Stream<String> authHeaders) {

        return authHeaders
                .map(AccessToken::fromString)
                .map(accessTokenManager::getUsernameFromToken)
                .findFirst()
                // .orElseThrow(BadCredentialsException::new)
                .map(user -> {
                    try {
                        return mailboxManager.createSystemSession(user, LOG);
                    } catch (MailboxException e) {
                        throw new RuntimeException(e);
                    }
                })
                ;
    }

    @Override
    public boolean checkAuthorizationHeader(Stream<String> authHeaders) {
        try {
            return authHeaders
                    .map(AccessToken::fromString)
                    .map(accessTokenManager::isValid)
                    .findAny()
                    .orElse(false);
        } catch (NotAnUUIDException e) {
            return false;
        }
    }
}
