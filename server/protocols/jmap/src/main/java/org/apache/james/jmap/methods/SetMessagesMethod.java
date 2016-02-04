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
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.mail.Flags;

import org.apache.james.jmap.model.ClientId;
import org.apache.james.jmap.model.MessageId;
import org.apache.james.jmap.model.MessageProperties;
import org.apache.james.jmap.model.SetError;
import org.apache.james.jmap.model.SetMessagesRequest;
import org.apache.james.jmap.model.SetMessagesResponse;
import org.apache.james.jmap.model.UpdateMessagePatch;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.FlagsUpdateCalculator;
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
import com.google.common.collect.ImmutableMap;

public class SetMessagesMethod<Id extends MailboxId> implements Method {

    private static final int LIMIT_BY_ONE = 1;
    private static final Logger LOGGER = LoggerFactory.getLogger(SetMessagesMethod.class);
    private static final Method.Request.Name METHOD_NAME = Method.Request.name("setMessages");
    private static final Method.Response.Name RESPONSE_NAME = Method.Response.name("messagesSet");

    private final MailboxMapperFactory<Id> mailboxMapperFactory;
    private final MailboxSessionMapperFactory<Id> mailboxSessionMapperFactory;

    @Inject
    @VisibleForTesting SetMessagesMethod(MailboxMapperFactory<Id> mailboxMapperFactory, MailboxSessionMapperFactory<Id> mailboxSessionMapperFactory) {
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
        SetMessagesResponse.Builder responseBuilder = SetMessagesResponse.builder();
        processDestroy(request.getDestroy(), mailboxSession, responseBuilder);
        processUpdates(request.getUpdate(), mailboxSession, responseBuilder);
        return responseBuilder.build();
    }

    private void processUpdates(Map<MessageId, UpdateMessagePatch> mapOfMessagePatchesById, MailboxSession mailboxSession,
                                SetMessagesResponse.Builder responseBuilder) {
        mapOfMessagePatchesById.entrySet().stream()
                .filter(kv -> kv.getValue().isValid())
                .forEach(kv -> update(kv.getKey(), kv.getValue(), mailboxSession, responseBuilder));
    }

    private void update(MessageId messageId, UpdateMessagePatch updateMessagePatch, MailboxSession mailboxSession, SetMessagesResponse.Builder builder){
        try {

            MessageMapper<Id> messageMapper = mailboxSessionMapperFactory.createMessageMapper(mailboxSession);
            Mailbox<Id> mailbox = mailboxMapperFactory.getMailboxMapper(mailboxSession).findMailboxByPath(messageId.getMailboxPath(mailboxSession));
            Iterator<MailboxMessage<Id>> mailboxMessage = messageMapper.findInMailbox(mailbox, MessageRange.one(messageId.getUid()), FetchType.Metadata, LIMIT_BY_ONE);

            if (! mailboxMessage.hasNext()) {
                addMessageIdNotFoundToResponse(messageId, builder);
                return;
            }
            MailboxMessage<Id> messageWithUpdatedFlags = applyMessagePatch(messageId, mailboxMessage.next(), updateMessagePatch, builder);
            savePatchedMessage(mailbox, messageId, messageWithUpdatedFlags, messageMapper);
        } catch(MailboxException e) {
            handleMessageUpdateException(messageId, builder, e);
        }
    }

    private void handleMessageUpdateException(MessageId messageId, SetMessagesResponse.Builder builder, MailboxException e) {
        LOGGER.error("An error occurred when updating a message", e);
        builder.notUpdated(ImmutableMap.of(messageId, SetError.builder()
                .type("anErrorOccurred")
                .description("An error occurred when updating a message")
                .build()));
    }

    private boolean savePatchedMessage(Mailbox<Id> mailbox, MessageId messageId,
                                       MailboxMessage<Id> message,
                                       MessageMapper<Id> messageMapper) throws MailboxException {
            return messageMapper.updateFlags(mailbox, new FlagsUpdateCalculator(message.createFlags(),
                    MessageManager.FlagsUpdateMode.REPLACE),
                    MessageRange.one(messageId.getUid()))
                    .hasNext()
                    ;
    }

    private void addMessageIdNotFoundToResponse(MessageId messageId, SetMessagesResponse.Builder builder) {
        builder.notUpdated(ImmutableMap.of( messageId,
                SetError.builder()
                        .type("notFound")
                        .properties(MessageProperties.MessageProperty.id)
                        .description("message not found")
                        .build()));
    }

    private MailboxMessage<Id> applyMessagePatch(MessageId messageId, MailboxMessage<Id> message, UpdateMessagePatch updatePatch, SetMessagesResponse.Builder builder) {
        Flags messageFlags = new Flags();
        if (!message.isSeen() && updatePatch.isUnread().isPresent() && !updatePatch.isUnread().get()) {
            messageFlags.add(Flags.Flag.SEEN);
        }
        if (!message.isAnswered() && updatePatch.isAnswered().isPresent() && updatePatch.isAnswered().get()) {
            messageFlags.add(Flags.Flag.ANSWERED);
        }
        if (!message.isFlagged() && updatePatch.isFlagged().isPresent() && updatePatch.isFlagged().get()) {
            messageFlags.add(Flags.Flag.FLAGGED);
        }
        message.setFlags(messageFlags);
        builder.updated(ImmutableList.of(messageId));

        return message;
    }

    private void processDestroy(List<MessageId> messageIds, MailboxSession mailboxSession, SetMessagesResponse.Builder responseBuilder) throws MailboxException {
        MessageMapper<Id> messageMapper = mailboxSessionMapperFactory.createMessageMapper(mailboxSession);
        Consumer<? super MessageId> delete = delete(messageMapper, mailboxSession, responseBuilder);

        messageIds.stream()
            .forEach(delete);
    }

    private Consumer<? super MessageId> delete(MessageMapper<Id> messageMapper, MailboxSession mailboxSession, SetMessagesResponse.Builder responseBuilder) {
        return (messageId) -> {
            try {
                Mailbox<Id> mailbox = mailboxMapperFactory
                        .getMailboxMapper(mailboxSession)
                        .findMailboxByPath(messageId.getMailboxPath(mailboxSession));

                Iterator<MailboxMessage<Id>> mailboxMessage = messageMapper.findInMailbox(mailbox, MessageRange.one(messageId.getUid()), FetchType.Metadata, LIMIT_BY_ONE);
                if (!mailboxMessage.hasNext()) {
                    responseBuilder.notDestroyed(messageId,
                            SetError.builder()
                            .type("notFound")
                            .description("The message " + messageId.serialize() + " can't be found")
                            .build());
                    return;
                }

                messageMapper.delete(mailbox, mailboxMessage.next());
                responseBuilder.destroyed(messageId);
            } catch (MailboxException e) {
                LOGGER.error("An error occurred when deleting a message", e);
                responseBuilder.notDestroyed(messageId,
                        SetError.builder()
                        .type("anErrorOccurred")
                        .description("An error occurred while deleting message " + messageId.serialize())
                        .build());
            }
        };
    }
}
