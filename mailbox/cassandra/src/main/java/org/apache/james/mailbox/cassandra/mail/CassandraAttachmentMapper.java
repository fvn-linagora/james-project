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

package org.apache.james.mailbox.cassandra.mail;

import static com.datastax.driver.core.querybuilder.QueryBuilder.delete;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.apache.james.mailbox.cassandra.table.CassandraAttachmentTable.BLOB;
import static org.apache.james.mailbox.cassandra.table.CassandraAttachmentTable.ID;
import static org.apache.james.mailbox.cassandra.table.CassandraAttachmentTable.TABLE_NAME;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import org.apache.commons.io.IOUtils;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.store.mail.AttachmentMapper;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;

public class CassandraAttachmentMapper implements AttachmentMapper {

    private final Session session;

    public CassandraAttachmentMapper(Session session) {
        this.session = session;
    }

    @Override
    public void endRequest() {
        // Do nothing
    }

    @Override
    public <T> T execute(Transaction<T> transaction) throws MailboxException {
        return transaction.run();
    }

    @Override
    public void add(String blobId, InputStream blob) {
        try {
            session.execute(
                    insertInto(TABLE_NAME)
                        .value(ID, blobId)
                        .value(BLOB, ByteBuffer.wrap(IOUtils.toByteArray(blob)))
                    );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public InputStream get(String blobId) {
        ResultSet resultSet = session.execute(
                select(BLOB)
                    .from(TABLE_NAME)
                    .where(eq(ID, blobId))
                );
        if (!resultSet.isExhausted()) {
            return IOUtils.toInputStream(resultSet.one().getBytes(BLOB).asCharBuffer().toString());
        }
        return null;
    }

    @Override
    public void remove(String blobId) {
        session.execute(
                delete()
                    .from(TABLE_NAME)
                    .where(eq(ID, blobId)));
    }

}
