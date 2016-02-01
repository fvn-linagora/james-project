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

package org.apache.james.jmap.methods;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.jmap.model.ClientId;
import org.apache.james.jmap.model.MessageId;
import org.apache.james.jmap.model.SetError;
import org.apache.james.jmap.model.SetMessagesRequest;
import org.apache.james.jmap.model.SetMessagesResponse;
import org.apache.james.jmap.model.SetMessagesResponse.Builder;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.MailboxMapperFactory;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class SetMessagesMethod<Id extends MailboxId> implements Method {

    private static final int LIMIT_BY_ONE = 1;
    private static final Logger LOGGER = LoggerFactory.getLogger(SetMessagesMethod.class);
    private static final Method.Request.Name METHOD_NAME = Method.Request.name("setMessages");
    private static final Method.Response.Name RESPONSE_NAME = Method.Response.name("messagesSet");

    private final MailboxMapperFactory<Id> mailboxMapperFactory;
    private final MailboxSessionMapperFactory<Id> mailboxSessionMapperFactory;

    @Inject
    @VisibleForTesting public SetMessagesMethod(MailboxMapperFactory<Id> mailboxMapperFactory, MailboxSessionMapperFactory<Id> mailboxSessionMapperFactory) {
        this.mailboxMapperFactory = mailboxMapperFactory;
        this.mailboxSessionMapperFactory = mailboxSessionMapperFactory;
    }

    @Override
    public Method.Request.Name requestHandled() {
        return METHOD_NAME;
    }

    @Override
    public Class<? extends JmapRequest> requestType() {
        return SetMessagesRequest.class;
    }

    public Stream<JmapResponse> process(JmapRequest request, ClientId clientId, MailboxSession mailboxSession) {
        Preconditions.checkArgument(request instanceof SetMessagesRequest);
        try {
            return Stream.of(
                    JmapResponse.builder().clientId(clientId)
                    .response(setMessagesResponse((SetMessagesRequest) request, mailboxSession))
                    .responseName(RESPONSE_NAME)
                    .build());
        } catch (MailboxException e) {
            return Stream.of(
                    JmapResponse.builder().clientId(clientId)
                    .error()
                    .responseName(RESPONSE_NAME)
                    .build());
        }
    }

    private SetMessagesResponse setMessagesResponse(SetMessagesRequest request, MailboxSession mailboxSession) throws MailboxException {
        SetMessagesResponse.Builder builder = SetMessagesResponse.builder();
        processDestroy(request.getDestroy(), mailboxSession, builder);
        return builder.build();
    }

    private void processDestroy(List<MessageId> destroy, MailboxSession mailboxSession, SetMessagesResponse.Builder builder) throws MailboxException {
        MessageMapper<Id> messageMapper = mailboxSessionMapperFactory.createMessageMapper(mailboxSession);
        destroy.stream()
            .map(messageId -> delete(messageId, messageMapper, mailboxSession, builder))
            .collect(Collectors.toList());
    }

    private boolean delete(MessageId messageId, MessageMapper<Id> messageMapper, MailboxSession mailboxSession, Builder builder) {
        try {
            Mailbox<Id> mailbox = mailboxMapperFactory.getMailboxMapper(mailboxSession).findMailboxByPath(messageId.getMailboxPath(mailboxSession));
            Iterator<MailboxMessage<Id>> mailboxMessage = messageMapper.findInMailbox(mailbox, MessageRange.one(messageId.getUid()), FetchType.Metadata, LIMIT_BY_ONE);
            if (!mailboxMessage.hasNext()) {
                builder.notDestroyed(ImmutableList.of(SetError.builder()
                        .type("anErrorOccured")
                        .description(messageId.serialize())
                        .build()));
                return false;
            }

            messageMapper.delete(mailbox, mailboxMessage.next());
            builder.destroyed(ImmutableList.of(messageId));
        } catch (MailboxException e) {
            LOGGER.error("An error occured when deleting a message", e);
            builder.notDestroyed(ImmutableList.of(SetError.builder()
                    .type("anErrorOccured")
                    .description(messageId.serialize())
                    .build()));
        }
        return true;
    }

}
