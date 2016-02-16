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
import java.util.TimeZone;
import java.util.stream.Collectors;

import org.apache.james.jmap.model.CreationMessage;
import org.apache.james.jmap.model.Emailer;
import org.apache.james.mime4j.dom.Header;
import org.apache.james.mime4j.dom.TextBody;
import org.apache.james.mime4j.field.Fields;
import org.apache.james.mime4j.message.BasicBodyFactory;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.message.DefaultMessageWriter;
import org.apache.james.mime4j.message.HeaderImpl;

import com.google.common.base.Throwables;

class MIMEMessageConverter {

    private final MessageWithId.CreationMessageEntry creationMessageEntry;

    MIMEMessageConverter(MessageWithId.CreationMessageEntry creationMessageEntry) {
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
                .map(Fields::from)
                .ifPresent(f -> messageHeaders.addField(f));
        newMessage.getFrom().map(this::convertEmailToMimeHeader)
                .map(Fields::from)
                .ifPresent(f -> messageHeaders.addField(f));

        // add Reply-To:
        messageHeaders.addField(Fields.replyTo(newMessage.getReplyTo().stream()
                .map(rt -> convertEmailToMimeHeader(rt))
                .collect(Collectors.toList())));
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

        // date(String fieldName, Date date, TimeZone zone)
        messageHeaders.addField(Fields.date("Date",
                Date.from(newMessage.getDate().toInstant()), TimeZone.getTimeZone(newMessage.getDate().getZone())
        ));

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
