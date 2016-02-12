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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.mail.Flags;
import javax.mail.internet.SharedInputStream;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.james.jmap.model.CreationMessage;
import org.apache.james.jmap.model.Emailer;
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
import org.apache.james.mime4j.dom.Header;
import org.apache.james.mime4j.dom.TextBody;
import org.apache.james.mime4j.field.Fields;
import org.apache.james.mime4j.message.BasicBodyFactory;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.message.DefaultMessageWriter;
import org.apache.james.mime4j.message.HeaderImpl;

import com.github.fge.lambdas.functions.ThrowingFunction;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SetMessagesCreationProcessor<Id extends MailboxId> {

    private static class CreationMessageEntry {
        String creationId;
        CreationMessage message;

        public CreationMessageEntry(String creationId, CreationMessage message) {
            this.creationId = creationId;
            this.message = message;
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(SetMessagesCreationProcessor.class);

    private final MailboxMapperFactory<Id> mailboxMapperFactory;
    private final MailboxManager mailboxManager;
    private final MailboxSessionMapperFactory<Id> mailboxSessionMapperFactory;

    @Inject
    @VisibleForTesting
    SetMessagesCreationProcessor(MailboxMapperFactory<Id> mailboxMapperFactory,
                                        MailboxManager mailboxManager,
                                        MailboxSessionMapperFactory<Id> mailboxSessionMapperFactory) {
        this.mailboxMapperFactory = mailboxMapperFactory;
        this.mailboxManager = mailboxManager;
        this.mailboxSessionMapperFactory = mailboxSessionMapperFactory;
    }

    public SetMessagesResponse process(SetMessagesRequest request, MailboxSession mailboxSession) {
        SetMessagesResponse.Builder responseBuilder = SetMessagesResponse.builder();
        Stream<CreationMessageEntry> messagesToCreate = request.getCreate().entrySet().stream()
                .map(e -> new CreationMessageEntry(e.getKey(), e.getValue()));
        messagesToCreate.map( nuMsg -> createEachMessage(nuMsg, mailboxSession));

        return responseBuilder.build();
    }

    private Message createEachMessage(CreationMessageEntry createdEntry, MailboxSession session) {
        try {
            MessageMapper<Id> messageMapper = mailboxSessionMapperFactory.createMessageMapper(session);

            // Find mailbox
            Optional<Mailbox> outbox = mailboxManager.search(MailboxQuery.builder(session)
                    .privateUserMailboxes().build(), session).stream()
                .map(MailboxMetaData::getPath)
                .filter(this::hasRoleOutbox)
                .map(loadMailbox(session))
                .findFirst();

            Date internalDate = Date.from(Instant.now()); // why not ?!

            byte[] messageContent = new MIMEMessageConverter(createdEntry).getContent();
            SharedInputStream content = new SharedByteArrayInputStream(messageContent);
            long size = messageContent.length;
            int bodyStartOctet = 0;

            Flags flags = getMessageFlags();
            PropertyBuilder propertyBuilder = buildPropertyBuilder();
            MailboxId mailboxId = outbox.get().getMailboxId();

            MailboxMessage<Id> newMailboxMessage = new SimpleMailboxMessage(internalDate, size,
                    bodyStartOctet, content, flags, propertyBuilder, mailboxId);

            messageMapper.add(outbox.orElseThrow(() -> new MailboxRoleNotFoundException(Role.OUTBOX)), newMailboxMessage);

            Function<Long, MessageId> buildMessageIdFromUid = uid -> MessageId.of(
                    String.format("%s|outbox|%d", session.getUser().getUserName(), uid));
            return Message.fromMailboxMessage(newMailboxMessage, buildMessageIdFromUid);

        } catch (MailboxException e) {
            throw Throwables.propagate(e);
        } catch (MailboxRoleNotFoundException e) {
            LOGGER.error("Could not find mailbox '%s' while trying to save message.", e.getRole().serialize());
            throw Throwables.propagate(e);
        }
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

    private Flags getMessageFlags() {
        return new Flags();
    }


    private static class MIMEMessageConverter {

        private final CreationMessageEntry creationMessageEntry;

        MIMEMessageConverter(CreationMessageEntry creationMessageEntry) {
            this.creationMessageEntry = creationMessageEntry;
        }

        byte[] getContent() {

            CreationMessage newMessage = creationMessageEntry.message;

            TextBody textBody = new BasicBodyFactory().textBody(newMessage.getTextBody().orElse(""));
            org.apache.james.mime4j.dom.Message message = new DefaultMessageBuilder().newMessage();
            message.setBody(textBody);

            Header messageHeaders = new HeaderImpl();

            // add From: and Sender: headers
            newMessage.getFrom().map(this::convertEmailToMimeHeader)
                    .map(mb -> Fields.from(mb))
                    .ifPresent(f -> messageHeaders.addField(f));
            newMessage.getFrom().map(this::convertEmailToMimeHeader)
                    .map(mb -> Fields.sender(mb))
                    .ifPresent(f -> messageHeaders.addField(f));
            // add To: headers
            messageHeaders.addField(Fields.to(newMessage.getTo().stream()
                    .map(this::convertEmailToMimeHeader)
                    .collect(Collectors.toList())));
            // add Cc: headers
            messageHeaders.addField(Fields.cc(newMessage.getCc().stream()
                    .map(this::convertEmailToMimeHeader)
                    .collect(Collectors.toList())));
            // add Bcc: headers
            messageHeaders.addField(Fields.bcc(newMessage.getBcc().stream()
                    .map(this::convertEmailToMimeHeader)
                    .collect(Collectors.toList())));
            // add Subject: header
            messageHeaders.addField(Fields.subject(newMessage.getSubject()));
            // set creation Id as MessageId: header
            messageHeaders.addField(Fields.messageId(creationMessageEntry.creationId));

            message.setHeader(messageHeaders);

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            DefaultMessageWriter writer = new DefaultMessageWriter();
            try {
                writer.writeMessage(message, buffer);
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
            return buffer.toByteArray();
        }

        private org.apache.james.mime4j.dom.address.Mailbox convertEmailToMimeHeader(Emailer address) {
            String[] splittedAddress = address.getEmail().split("@", 2);
            return new org.apache.james.mime4j.dom.address.Mailbox(address.getName(), null,
                    splittedAddress[0], splittedAddress[1]);
        }
    }

    private static class MailboxRoleNotFoundException extends RuntimeException {

        final Role role;

        public MailboxRoleNotFoundException(Role role) {
            super(String.format("Could not find any mailbox with role '%s'", role.serialize()));
            this.role = role;
        }

        public Role getRole() {
            return role;
        }
    }
}
