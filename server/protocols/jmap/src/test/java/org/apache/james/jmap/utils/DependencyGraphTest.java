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

package org.apache.james.jmap.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.Test;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class DependencyGraphTest {

    @Test
    public void getOrderedShouldReturnOrderedMailbox() {
        Commit a = new Commit("A");
        Commit b = new Commit("B", a);
        Commit c = new Commit("C", b);

        DependencyGraph<Commit> graph = new DependencyGraph<>(Commit::getParent);
        Set<Commit> mailboxes = ImmutableSet.of(b, a, c);
        mailboxes.stream().forEach(graph::registerItem);

        List<String> orderedMailboxes = graph.getBuildChain()
                .map(Commit::getMessage)
                .collect(Collectors.toList());

        assertThat(orderedMailboxes).containsExactly("A", "B", "C");
    }

    @Test
    public void getOrderedWithEmptyGraphShouldReturnEmpty() {
        DependencyGraph<Commit> graph = new DependencyGraph<>(m -> null);
        assertThat(graph.getBuildChain()).isEmpty();
    }

    @Test
    public void getOrderedOnIsolatedVerticesShouldReturnSameOrder() {
        DependencyGraph<Commit> graph = new DependencyGraph<>(m -> Optional.empty());
        ImmutableList<Commit> isolatedMailboxes = ImmutableList.of(new Commit("A"), new Commit("B"), new Commit("C"));
        isolatedMailboxes.stream().forEach(graph::registerItem);

        List<Commit> orderedResultList = graph.getBuildChain().collect(Collectors.toList());

        assertThat(orderedResultList).isEqualTo(isolatedMailboxes);
    }

    private static class Commit {
        private final String hash;
        private final String message;
        private final Optional<Commit> parent;

        @VisibleForTesting
        Commit(String message) {
            this(message, null);
        }

        @VisibleForTesting
        Commit(String message, Commit parent) {
            Preconditions.checkArgument(message != null);
            this.hash = DigestUtils.sha1Hex(message);
            this.message = message;
            this.parent = Optional.ofNullable(parent);
        }

        public String getHash() {
            return hash;
        }

        public Optional<Commit> getParent() {
            return parent;
        }

        public String getMessage() {
            return message;
        }

        public String toString() {
            return "#" + hash.substring(0, 6) + " (#" + parent.map(p -> p.getHash().substring(0, 6)).orElse("(none)") + ") - " + message;
        }
    }
}
