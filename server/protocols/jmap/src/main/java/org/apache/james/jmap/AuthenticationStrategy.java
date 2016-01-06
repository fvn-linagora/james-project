package org.apache.james.jmap;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;

public interface AuthenticationStrategy<R> {
    MailboxSession createMailboxSession(R requestHeaders) throws MailboxException;
    boolean checkAuthorizationHeader(R requestHeaders);
}
