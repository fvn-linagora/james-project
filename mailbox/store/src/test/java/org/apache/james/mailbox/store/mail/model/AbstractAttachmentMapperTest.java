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

package org.apache.james.mailbox.store.mail.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Charsets;

/**
 * Generic purpose tests for your implementation MailboxMapper.
 * 
 * You then just need to instantiate your mailbox mapper and an IdGenerator.
 */
public abstract class AbstractAttachmentMapperTest<Id extends MailboxId> {
    

    private final MapperProvider<Id> mapperProvider;
    private AttachmentMapper attachmentMapper;

    public AbstractAttachmentMapperTest(MapperProvider<Id> mapperProvider) {
        this.mapperProvider = mapperProvider;
    }

    @Before
    public void setUp() throws MailboxException {
        mapperProvider.ensureMapperPrepared();
        attachmentMapper = mapperProvider.createAttachmentMapper();
    }

    @After
    public void tearDown() throws MailboxException {
        mapperProvider.clearMapper();
    }

    @Test
    public void addShouldWork() throws IOException {
        // Given
        String blobId = "blobId";
        String expected = "content";

        // When
        attachmentMapper.add(blobId, IOUtils.toInputStream(expected, Charsets.UTF_8));

        // Then
        InputStream attachment = attachmentMapper.get(blobId);
        assertThat(IOUtils.toString(attachment, Charsets.UTF_8)).isEqualTo(expected);
    }

    @Test
    public void getShouldWork() throws IOException {
        // Given
        String blobId = "blobId";
        String expected = "content";

        attachmentMapper.add(blobId, IOUtils.toInputStream(expected, Charsets.UTF_8));

        // When
        InputStream attachment = attachmentMapper.get(blobId);

        // Then
        assertThat(IOUtils.toString(attachment, Charsets.UTF_8)).isEqualTo(expected);
    }

    @Test
    public void deleteShouldWork() throws IOException {
        // Given
        String blobId = "blobId";
        String expected = "content";

        attachmentMapper.add(blobId, IOUtils.toInputStream(expected, Charsets.UTF_8));

        // When
        attachmentMapper.remove(blobId);

        // Then
        assertThat(attachmentMapper.get(blobId)).isNull();
    }
}
