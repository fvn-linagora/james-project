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

import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.builder.DirectedGraphBuilder;
import org.jgrapht.traverse.AbstractGraphIterator;
import org.jgrapht.traverse.TopologicalOrderIterator;

public class DependencyGraph<T> {


    private final DirectedGraphBuilder<T, DefaultEdge, DefaultDirectedGraph<T, DefaultEdge>> builder;

    private final Function<T, String> getName;
    private final Function<T, T> getParent;


    public DependencyGraph(Function<T, String> getName, Function<T, T> getParent) {
        this.getName = getName;
        this.getParent = getParent;
        this.builder = new DirectedGraphBuilder<>(new DefaultDirectedGraph<>(DefaultEdge.class));
    }

    public void registerItem(T item) {
        builder.addVertex(item);
        T parentNode = getParent.apply(item);
        if (parentNode != null) {
            builder.addEdge(parentNode, item);
        }
    }

    public Stream<T> getOrdered() {
        DefaultDirectedGraph<T, DefaultEdge> graph = builder.build();
        AbstractGraphIterator<T, DefaultEdge> i = new TopologicalOrderIterator<>(graph);
        Iterable<T> iterable = () -> i;
        return StreamSupport.stream(iterable.spliterator(), false);
    }
}
