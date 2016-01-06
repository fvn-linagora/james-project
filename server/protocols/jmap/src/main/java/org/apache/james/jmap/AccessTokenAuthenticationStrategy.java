package org.apache.james.jmap;

import org.apache.james.jmap.api.AccessTokenManager;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.jmap.api.access.exceptions.NotAnUUIDException;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.BadCredentialsException;
import org.apache.james.mailbox.exception.MailboxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Optional;

public class AccessTokenAuthenticationStrategy implements AuthenticationStrategy<Optional<String>> {

    private static final Logger LOG = LoggerFactory.getLogger(AccessTokenAuthenticationStrategy.class);

    private final AccessTokenManager accessTokenManager;
    private final MailboxManager mailboxManager;

    @Inject
    public AccessTokenAuthenticationStrategy(AccessTokenManager accessTokenManager, MailboxManager mailboxManager) {
        this.accessTokenManager = accessTokenManager;
        this.mailboxManager = mailboxManager;
    }

    @Override
    public MailboxSession createMailboxSession(Optional<String> authHeader) throws MailboxException {
        String username = authHeader
                .map(AccessToken::fromString)
                .map(accessTokenManager::getUsernameFromToken)
                .orElseThrow(BadCredentialsException::new);
        return mailboxManager.createSystemSession(username, LOG);
    }

    @Override
    public boolean checkAuthorizationHeader(Optional<String> authHeader) {
        try {
            return authHeader
                    .map(AccessToken::fromString)
                    .map(accessTokenManager::isValid)
                    .orElse(false);
        } catch (NotAnUUIDException e) {
            return false;
        }
    }

}
