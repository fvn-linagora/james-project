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

import java.util.Date;
import java.util.Optional;
import java.util.function.Function;

import javax.inject.Inject;
import javax.mail.Flags;
import javax.mail.internet.SharedInputStream;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.james.jmap.exceptions.MailboxRoleNotFoundException;
import org.apache.james.jmap.model.CreationMessage;
import org.apache.james.jmap.model.Message;
import org.apache.james.jmap.model.MessageId;
import org.apache.james.jmap.model.SetMessagesRequest;
import org.apache.james.jmap.model.SetMessagesResponse;
import org.apache.james.jmap.model.mailbox.Role;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MailboxQuery;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.MailboxMapperFactory;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.functions.ThrowingFunction;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;

public class SetMessagesCreationProcessor<Id extends MailboxId> implements SetMessagesProcessor<Id> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SetMessagesCreationProcessor.class);

    private final MailboxMapperFactory<Id> mailboxMapperFactory;
    private final MailboxManager mailboxManager;
    private final MailboxSessionMapperFactory<Id> mailboxSessionMapperFactory;
    private final MIMEMessageConverter mimeMessageConverter;

    @Inject
    @VisibleForTesting
    SetMessagesCreationProcessor(MailboxMapperFactory<Id> mailboxMapperFactory,
                                 MailboxManager mailboxManager,
                                 MailboxSessionMapperFactory<Id> mailboxSessionMapperFactory, MIMEMessageConverter mimeMessageConverter) {
        this.mailboxMapperFactory = mailboxMapperFactory;
        this.mailboxManager = mailboxManager;
        this.mailboxSessionMapperFactory = mailboxSessionMapperFactory;
        this.mimeMessageConverter = mimeMessageConverter;
    }

    @Override
    public SetMessagesResponse process(SetMessagesRequest request, MailboxSession mailboxSession) {
        SetMessagesResponse.Builder responseBuilder = request.getCreate().entrySet().stream()
                .map(e -> new MessageWithId.CreationMessageEntry(e.getKey(), e.getValue()))
                .map(nuMsg -> createEachMessage(nuMsg, mailboxSession))
                .reduce(SetMessagesResponse.builder(),
                        (builder, msg) -> builder.created(ImmutableMap.of(msg.creationId, msg.message)),
                        (builder1, builder2) -> builder1.created(builder2.build().getCreated())
                );
        return responseBuilder.build();
    }

    protected MessageWithId<Message> createEachMessage(MessageWithId.CreationMessageEntry createdEntry, MailboxSession session) {
        try {
            MessageMapper<Id> messageMapper = mailboxSessionMapperFactory.createMessageMapper(session);
            Optional<Mailbox> outbox = getOutbox(session);
            MailboxMessage<Id> newMailboxMessage = buildMailboxMessage(createdEntry, outbox);

            messageMapper.add(outbox.orElseThrow(() -> new MailboxRoleNotFoundException(Role.OUTBOX)), newMailboxMessage);

            Function<Long, MessageId> buildMessageIdFromUid = uid -> MessageId.of(
                    String.format("%s|outbox|%d", session.getUser().getUserName(), uid));
            return new MessageWithId<>(createdEntry.creationId, Message.fromMailboxMessage(newMailboxMessage, buildMessageIdFromUid));

        } catch (MailboxException e) {
            throw Throwables.propagate(e);
        } catch (MailboxRoleNotFoundException e) {
            LOGGER.error("Could not find mailbox '%s' while trying to save message.", e.getRole().serialize());
            throw Throwables.propagate(e);
        }
    }

    private MailboxMessage<Id> buildMailboxMessage(MessageWithId.CreationMessageEntry createdEntry, Optional<Mailbox> outbox) {
        byte[] messageContent = mimeMessageConverter.getMimeContent(createdEntry);
        SharedInputStream content = new SharedByteArrayInputStream(messageContent);
        long size = messageContent.length;
        int bodyStartOctet = 0;

        Flags flags = getMessageFlags(createdEntry.message);
        PropertyBuilder propertyBuilder = buildPropertyBuilder();
        MailboxId mailboxId = outbox.get().getMailboxId();
        Date internalDate = Date.from(createdEntry.message.getDate().toInstant());

        return new SimpleMailboxMessage(internalDate, size,
                bodyStartOctet, content, flags, propertyBuilder, mailboxId);
    }

    private Optional<Mailbox> getOutbox(MailboxSession session) throws MailboxException {
        return mailboxManager.search(MailboxQuery.builder(session)
                .privateUserMailboxes().build(), session).stream()
            .map(MailboxMetaData::getPath)
            .filter(this::hasRoleOutbox)
            .map(loadMailbox(session))
            .findFirst();
    }

    private boolean hasRoleOutbox(MailboxPath mailBoxPath) {
        return Role.from(mailBoxPath.getName())
                .map(Role.OUTBOX::equals)
                .orElse(false);
    }

    private ThrowingFunction<MailboxPath, Mailbox> loadMailbox(MailboxSession session) {
        return path -> mailboxMapperFactory.getMailboxMapper(session).findMailboxByPath(path);
    }

    private PropertyBuilder buildPropertyBuilder() {
        return new PropertyBuilder();
    }

    private Flags getMessageFlags(CreationMessage message) {
        Flags result = new Flags();
        if (!message.isIsUnread()) {
            result.add(Flags.Flag.SEEN);
        }
        if (message.isIsFlagged()) {
            result.add(Flags.Flag.FLAGGED);
        }
        if (message.isIsAnswered() || message.getInReplyToMessageId().isPresent()) {
            result.add(Flags.Flag.ANSWERED);
        }
        if (message.isIsDraft()) {
            result.add(Flags.Flag.DRAFT);
        }
        return result;
    }
}
