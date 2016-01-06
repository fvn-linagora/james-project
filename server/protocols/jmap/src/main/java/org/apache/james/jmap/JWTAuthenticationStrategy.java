package org.apache.james.jmap;

import org.apache.james.jmap.crypto.JwtTokenVerifier;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Optional;

public class JWTAuthenticationStrategy implements AuthenticationStrategy<Optional<String>> {

    private static final Logger LOG = LoggerFactory.getLogger(JWTAuthenticationStrategy.class);
    private final JwtTokenVerifier tokenManager;
    private final MailboxManager mailboxManager;

    @Inject
    public JWTAuthenticationStrategy(JwtTokenVerifier tokenManager, MailboxManager mailboxManager) {
        this.tokenManager = tokenManager;
        this.mailboxManager = mailboxManager;
    }

    @Override
    public MailboxSession createMailboxSession(Optional<String> authHeader) throws MailboxException {
        return mailboxManager.createSystemSession(
                tokenManager.extractLogin(extractToken(authHeader).get()), LOG);
    }

    @Override
    public boolean checkAuthorizationHeader(Optional<String> authHeader) {
         return extractToken(authHeader)
                 .map(t -> tokenManager.verify(t))
                 .orElse(false);
    }

    private Optional<String> extractToken(Optional<String> authHeader) {
        return authHeader
                .filter(h -> h.startsWith("Bearer "))
                .map(s -> s.substring("Bearer ".length()));
    }
}
