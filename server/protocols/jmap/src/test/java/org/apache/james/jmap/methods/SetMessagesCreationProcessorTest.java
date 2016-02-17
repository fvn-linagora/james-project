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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;

import org.apache.james.jmap.model.CreationMessage;
import org.apache.james.jmap.model.Emailer;
import org.apache.james.jmap.model.Message;
import org.apache.james.jmap.model.SetMessagesRequest;
import org.apache.james.jmap.model.SetMessagesResponse;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class SetMessagesCreationProcessorTest {

    @Test
    public void processShouldReturnEmptyCreatedWhenRequestHasEmptyCreate() {
        SetMessagesCreationProcessor sut = new SetMessagesCreationProcessor(null, null, null, null);
        SetMessagesRequest requestWithEmptyCreate = SetMessagesRequest.builder().build();

        SetMessagesResponse result = sut.process(requestWithEmptyCreate, null);

        assertThat(result.getCreated()).isEmpty();
        assertThat(result.getNotCreated()).isEmpty();
    }

    @Test
    public void processShouldReturnNonEmptyCreatedWhenRequestHasNonEmptyCreate() throws MailboxException {

        MessageMapper stubMapper = mock(MessageMapper.class);
        MailboxSessionMapperFactory mockSessionMapperFactory = mock(MailboxSessionMapperFactory.class);
        when(mockSessionMapperFactory.createMessageMapper(any(MailboxSession.class)))
                .thenReturn(stubMapper);

        SetMessagesCreationProcessor sut = new SetMessagesCreationProcessor<MailboxId>(null, null, mockSessionMapperFactory, null) {
            @Override
            protected MessageWithId<Message> createEachMessage(MessageWithId.CreationMessageEntry createdEntry, MailboxSession session) {
                return new MessageWithId<>(createdEntry.creationId, getFakeMessage());
            }
        };

        SetMessagesRequest creationRequest = SetMessagesRequest.builder()
                .create(ImmutableMap.of("anything-really", CreationMessage.builder()
                    .from(Emailer.builder().name("alice").email("alice@example.com").build())
                    .to(ImmutableList.of(Emailer.builder().name("bob").email("bob@example.com").build()))
                    .subject("Hey! ")
                    .mailboxIds(ImmutableList.of("mailboxId"))
                    .build()
                ))
                .build()
                ;

        SetMessagesResponse result = sut.process(creationRequest, null);

        assertThat(result.getCreated()).isNotEmpty();
        assertThat(result.getNotCreated()).isEmpty();
    }

    private Message getFakeMessage() {
        return Message.builder()
                .id(org.apache.james.jmap.model.MessageId.of("user|outbox|1"))
                .blobId("anything")
                .threadId("anything")
                .mailboxIds(ImmutableList.of("mailboxId"))
                .headers(ImmutableMap.of())
                .subject("anything")
                .size(0)
                .date(ZonedDateTime.now())
                .preview("anything")
                .build();
    }
}