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

import java.util.Iterator;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.builder.DirectedGraphBuilder;
import org.jgrapht.traverse.TopologicalOrderIterator;

public class DependencyGraph<T> {

    private final DirectedGraphBuilder<T, DefaultEdge, DefaultDirectedGraph<T, DefaultEdge>> builder;
    private final Function<T, Optional<T>> getParent;


    public DependencyGraph(Function<T, Optional<T>> getParent) {
        this.getParent = getParent;
        this.builder = new DirectedGraphBuilder<>(new DefaultDirectedGraph<>(DefaultEdge.class));
    }

    public void registerItem(T item) {
        builder.addVertex(item);
        getParent.apply(item)
                .map(parentNode -> builder.addEdge(parentNode, item));
    }

    public Stream<T> getBuildChain() {
        DefaultDirectedGraph<T, DefaultEdge> graph = builder.build();
        return getStreamFromIterator(new TopologicalOrderIterator<>(graph));
    }

    private static <T> Stream<T> getStreamFromIterator(Iterator<T> iterator) {
        Iterable<T> iterable = () -> iterator;
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    public String toString() {
        return builder.build().toString();
    }
}
