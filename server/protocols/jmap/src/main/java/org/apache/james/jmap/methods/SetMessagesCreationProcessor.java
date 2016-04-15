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

import java.io.IOException;
import java.util.AbstractMap;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.mail.Flags;
import javax.mail.MessagingException;
import javax.mail.internet.SharedInputStream;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.james.jmap.exceptions.MailboxRoleNotFoundException;
import org.apache.james.jmap.model.CreationMessage;
import org.apache.james.jmap.model.CreationMessageId;
import org.apache.james.jmap.model.Message;
import org.apache.james.jmap.model.MessageId;
import org.apache.james.jmap.model.MessageProperties;
import org.apache.james.jmap.model.MessageProperties.MessageProperty;
import org.apache.james.jmap.model.SetError;
import org.apache.james.jmap.model.SetMessagesRequest;
import org.apache.james.jmap.model.SetMessagesResponse;
import org.apache.james.jmap.model.mailbox.Role;
import org.apache.james.jmap.send.MailFactory;
import org.apache.james.jmap.send.MailMetadata;
import org.apache.james.jmap.send.MailSpool;
import org.apache.james.jmap.utils.SystemMailboxesProvider;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.mailet.Mail;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;

public class SetMessagesCreationProcessor<Id extends MailboxId> implements SetMessagesProcessor<Id> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SetMessagesCreationProcessor.class);

    private final MailboxSessionMapperFactory<Id> mailboxSessionMapperFactory;
    private final MIMEMessageConverter mimeMessageConverter;
    private final MailSpool mailSpool;
    private final MailFactory<Id> mailFactory;
    private final SystemMailboxesProvider<Id> systemMailboxesProvider;

    @VisibleForTesting
    @Inject
    SetMessagesCreationProcessor(MailboxSessionMapperFactory<Id> mailboxSessionMapperFactory,
                                 MIMEMessageConverter mimeMessageConverter,
                                 MailSpool mailSpool,
                                 MailFactory<Id> mailFactory,
                                 SystemMailboxesProvider<Id> systemMailboxesProvider) {
        this.mailboxSessionMapperFactory = mailboxSessionMapperFactory;
        this.mimeMessageConverter = mimeMessageConverter;
        this.mailSpool = mailSpool;
        this.mailFactory = mailFactory;
        this.systemMailboxesProvider = systemMailboxesProvider;
    }

    @Override
    public SetMessagesResponse process(SetMessagesRequest request, MailboxSession mailboxSession) {
        return SetMessagesResponse.builder()
                .notCreated(handleErrors(request, mailboxSession))
                .mergeWith(handleOutboxMessages(request, mailboxSession))
                .build();
    }

    private SetMessagesResponse.Builder handleOutboxMessages(SetMessagesRequest request, MailboxSession mailboxSession) {
        Mailbox<Id> outbox = getOutbox(mailboxSession);
        Predicate<MessageWithId.CreationMessageEntry> isASendingRequest = creationRequest -> isRequestForSending(creationRequest.getMessage(), mailboxSession, CreationMessage::isValid);
        Function<Map.Entry<CreationMessageId, CreationMessage>, MessageWithId.CreationMessageEntry> toMessageWithId = entry -> new MessageWithId.CreationMessageEntry(entry.getKey(), entry.getValue());
        Function<Pair<MailboxSession, Mailbox<Id>>, Function<Long, MessageId>> messageIdGenerator = sessionAndMailboxId -> curryIdGenerator(mailboxSession, outbox);
        Function<MessageWithId.CreationMessageEntry, MessageWithId<Message>> fileMessageInOutboxAndSendIt = curryMessageSender(mailboxSession, outbox, messageIdGenerator);

        SetMessagesResponse.Builder response = SetMessagesResponse.builder();
        request.getCreate().entrySet().stream()
                .map(toMessageWithId)
                .filter(isASendingRequest)
                .map(fileMessageInOutboxAndSendIt)
                .forEach(msg -> response.created(msg.getCreationId(), msg.getMessage()));
        return response;
    }

    private Function<Long, MessageId> curryIdGenerator(MailboxSession session, Mailbox<Id> outbox) {
        MailboxPath outboxPath = new MailboxPath(session.getPersonalSpace(), session.getUser().getUserName(), outbox.getName());
        return uid -> new MessageId(session.getUser(), outboxPath, uid);
    }

    private Mailbox<Id> getOutbox(MailboxSession mailboxSession) {
        Role outbox = Role.OUTBOX;
        return systemMailboxesProvider.getStreamOfMailboxesFromRole(outbox, mailboxSession).findFirst()
                .orElseThrow(() -> new MailboxRoleNotFoundException(outbox));
    }

    private Map<CreationMessageId, SetError> handleErrors(SetMessagesRequest request, MailboxSession mailboxSession) {
        Predicate<CreationMessage> notImplementedPredicate = getIsMessageSetInDraftPredicate(mailboxSession);
        Predicate<CreationMessage> invalidMessagesTester = message -> !message.isValid();

        Set<Map.Entry<CreationMessageId, CreationMessage>> creationRequestEntries = request.getCreate().entrySet();
        Map<CreationMessageId, SetError> mapOfNotImplEntries = getMapOfNotImplementedErrors(notImplementedPredicate, creationRequestEntries);
        List<AbstractMap.SimpleEntry<CreationMessageId, SetError>> invalidArgumentsErrors = getInvalidArgumentsErrors(creationRequestEntries, invalidMessagesTester, mapOfNotImplEntries);
        // Merge maps of SetErrors
        return Stream.concat(mapOfNotImplEntries.entrySet().stream(), invalidArgumentsErrors.stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private boolean isRequestForSending(CreationMessage messageWithId, MailboxSession session,
                                        Predicate<CreationMessage> validMessagesTester) {
        Predicate<Mailbox<Id>> isMessageCreatedInOutbox = box -> messageWithId.getMailboxIds().contains(box.getMailboxId().serialize());
        boolean isMessageSetInOutbox = systemMailboxesProvider.getStreamOfMailboxesFromRole(Role.OUTBOX, session)
                .anyMatch(isMessageCreatedInOutbox);
        return validMessagesTester.test(messageWithId) && isMessageSetInOutbox;
    }

    private Predicate<CreationMessage> getIsMessageSetInDraftPredicate(MailboxSession mailboxSession) {
        Optional<Id> draftsId = systemMailboxesProvider.getStreamOfMailboxesFromRole(Role.DRAFTS, mailboxSession)
                .map(Mailbox::getMailboxId)
                .findFirst();
        return creationMessage -> draftsId
                .map(MailboxId::serialize)
                .map(id -> creationMessage.getMailboxIds().contains(id))
                .orElse(false);
    }

    private Map<CreationMessageId, SetError> getMapOfNotImplementedErrors(Predicate<CreationMessage> notImplementedPredicate,
                                                                          Set<Map.Entry<CreationMessageId, CreationMessage>> creationRequestEntries) {

        SetError notImplementedError = SetError.builder().type("error").description("Not yet implemented").build();

        return creationRequestEntries.stream()
                .filter(e -> notImplementedPredicate.test(e.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, e -> notImplementedError));
    }

    private List<AbstractMap.SimpleEntry<CreationMessageId, SetError>> getInvalidArgumentsErrors(
            Set<Map.Entry<CreationMessageId, CreationMessage>> creationRequestEntries,
            Predicate<CreationMessage> invalidMessagesTester, Map<CreationMessageId, SetError> mapOfNotImplEntries) {

        Predicate<Map.Entry<CreationMessageId, CreationMessage>> isCreationRequestImplemented = e -> !mapOfNotImplEntries.containsKey(e.getKey());
        // Deal with invalid arguments but only for entries that have not been flagged yet (ie not impl...)
        return creationRequestEntries.stream()
                .filter(e -> isCreationRequestImplemented.test(e) && invalidMessagesTester.test(e.getValue()))
                .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), buildSetErrorFromValidationResult(e.getValue().validate())))
                .collect(Collectors.toList());
    }

    private SetError buildSetErrorFromValidationResult(List<ValidationResult> validationErrors) {
        String formattedValidationErrorMessage = validationErrors.stream()
                .map(err -> err.getProperty() + ": " + err.getErrorMessage())
                .collect(Collectors.joining("\\n"));
        Splitter propertiesSplitter = Splitter.on(',').trimResults().omitEmptyStrings();
        Set<MessageProperties.MessageProperty> properties = validationErrors.stream()
                .flatMap(err -> propertiesSplitter.splitToList(err.getProperty()).stream())
                .flatMap(MessageProperty::find)
                .collect(Collectors.toSet());
        return SetError.builder()
                .type("invalidProperties")
                .properties(properties)
                .description(formattedValidationErrorMessage)
                .build();
    }

    @VisibleForTesting
    protected MessageWithId<Message> createMessageInOutboxAndSend(MessageWithId.CreationMessageEntry createdEntry,
                                                           MailboxSession session,
                                                           Mailbox<Id> outbox, Function<Long, MessageId> buildMessageIdFromUid) {
        try {
            MessageMapper<Id> messageMapper = mailboxSessionMapperFactory.createMessageMapper(session);
            MailboxMessage<Id> newMailboxMessage = buildMailboxMessage(createdEntry, outbox);
            messageMapper.add(outbox, newMailboxMessage);
            Message jmapMessage = Message.fromMailboxMessage(newMailboxMessage, buildMessageIdFromUid);
            sendMessage(newMailboxMessage, jmapMessage, session);
            return new MessageWithId<>(createdEntry.getCreationId(), jmapMessage);
        } catch (MailboxException | MessagingException | IOException e) {
            throw Throwables.propagate(e);
        } catch (MailboxRoleNotFoundException e) {
            LOGGER.error("Could not find mailbox '%s' while trying to save message.", e.getRole().serialize());
            throw Throwables.propagate(e);
        }
    }

    private Function<MessageWithId.CreationMessageEntry, MessageWithId<Message>> curryMessageSender(
            MailboxSession mailboxSession,
            Mailbox<Id> outbox,
            Function<Pair<MailboxSession, Mailbox<Id>>, Function<Long, MessageId>> messageIdGenerator) {
        return createdMessage -> createMessageInOutboxAndSend(createdMessage, mailboxSession, outbox,
                messageIdGenerator.apply(Pair.with(mailboxSession, outbox)));
    }

    private MailboxMessage<Id> buildMailboxMessage(MessageWithId.CreationMessageEntry createdEntry, Mailbox<Id> outbox) {
        byte[] messageContent = mimeMessageConverter.convert(createdEntry);
        SharedInputStream content = new SharedByteArrayInputStream(messageContent);
        long size = messageContent.length;
        int bodyStartOctet = 0;

        Flags flags = getMessageFlags(createdEntry.getMessage());
        PropertyBuilder propertyBuilder = buildPropertyBuilder();
        Id mailboxId = outbox.getMailboxId();
        Date internalDate = Date.from(createdEntry.getMessage().getDate().toInstant());

        return new SimpleMailboxMessage<>(internalDate, size,
                bodyStartOctet, content, flags, propertyBuilder, mailboxId);
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

    private void sendMessage(MailboxMessage<Id> mailboxMessage, Message jmapMessage, MailboxSession session) throws MessagingException, IOException {
        Mail mail = mailFactory.build(mailboxMessage, jmapMessage);
        MailMetadata metadata = new MailMetadata(jmapMessage.getId(), session.getUser().getUserName());
        mailSpool.send(mail, metadata);
    }
}
