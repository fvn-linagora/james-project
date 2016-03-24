/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.james.jmap.utils;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.james.jmap.model.mailbox.Mailbox;

public class MailboxPathSorter {

    public Stream<Mailbox> sortFromRootToLeaf(List<Mailbox> mailboxes) {

        Map<String, Mailbox> mapOfMailboxesById = mailboxes.stream()
                .collect(Collectors.toMap(x -> x.getId(), x -> x));

        DependencyGraph<Mailbox> graph = new DependencyGraph<>(m -> m.getId(),
                m -> getParentMailbox(m, mapOfMailboxesById));
        mailboxes.stream().forEach(graph::registerItem);

        return graph.getOrdered();
    }

    private Mailbox getParentMailbox(Mailbox m, Map<String, Mailbox> map) {
        return m.getParentId()
                .map(p -> map.get(p))
                .orElse(null);
    }

    public Stream<Mailbox> sortFromLeafToRoot(List<Mailbox> mailboxes) {
        Iterator<Mailbox> mailboxIterator = sortFromRootToLeaf(mailboxes)
                .collect(Collectors.toCollection(LinkedList::new))
                .descendingIterator();

        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(mailboxIterator,
                        Spliterator.ORDERED), false);
    }
}
