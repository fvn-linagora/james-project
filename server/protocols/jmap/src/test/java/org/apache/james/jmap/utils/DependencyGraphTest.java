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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.Test;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class DependencyGraphTest {

    @Test
    public void getOrderedShouldReturnOrderedMailbox() {
        FakeCommitNode a = new FakeCommitNode("A");
        FakeCommitNode b = new FakeCommitNode(Optional.of("B"), Optional.of(a));
        FakeCommitNode c = new FakeCommitNode(Optional.of("C"), Optional.of(b));

        Map<String, FakeCommitNode > mapOfMailboxesById = ImmutableList.of(b, a, c).stream()
                .collect(Collectors.toMap(FakeCommitNode::getHash, x -> x));

        DependencyGraph<FakeCommitNode> graph = new DependencyGraph<>(FakeCommitNode::getHash, m -> m.getParent().orElse(null));
        mapOfMailboxesById.values().stream().forEach(graph::registerItem);

        List<String> orderedMailboxes = graph.getOrdered()
                .map(FakeCommitNode::getMessage)
                .map(o -> o.orElse("(null)"))
                .collect(Collectors.toList());

        assertThat(orderedMailboxes).containsExactly("A", "B", "C");
    }

    @Test
    public void getOrderedWithEmptyGraphShouldReturnEmpty() {
        DependencyGraph<FakeCommitNode> graph = new DependencyGraph<>(FakeCommitNode::getHash, m -> null);
        assertThat(graph.getOrdered()).isEmpty();
    }

    @Test
    public void getOrderedOnIsolatedVerticesShouldReturnSameOrder() {
        DependencyGraph<FakeCommitNode> graph = new DependencyGraph<>(FakeCommitNode::getHash, m -> null);
        ImmutableList<FakeCommitNode> isolatedMailboxes = ImmutableList.of(new FakeCommitNode("A"), new FakeCommitNode("B"), new FakeCommitNode("C"));
        isolatedMailboxes.stream().forEach(graph::registerItem);

        List<FakeCommitNode> orderedResultList = graph.getOrdered().collect(Collectors.toList());

        assertThat(orderedResultList).isEqualTo(isolatedMailboxes);
    }

    private static class FakeCommitNode {
        private final String hash;
        private final Optional<String> message;
        private final Optional<FakeCommitNode> parent;

        @VisibleForTesting
        FakeCommitNode(String message) {
            this(Optional.ofNullable(message), Optional.empty());
        }

        @VisibleForTesting
        FakeCommitNode(Optional<String> message, Optional<FakeCommitNode> parent) {
            this.hash = DigestUtils.sha1Hex(message.orElse("(empty)"));
            this.message = message;
            this.parent = parent;
        }

        public String getHash() {
            return hash;
        }

        public Optional<FakeCommitNode> getParent() {
            return parent;
        }

        public Optional<String> getMessage() {
            return message;
        }

        public String toString() {
            return "#" + hash + "(#" + parent.map(p -> p.getHash()).orElse("(none)") + ") - " + message.orElse("");
        }
    }
}
