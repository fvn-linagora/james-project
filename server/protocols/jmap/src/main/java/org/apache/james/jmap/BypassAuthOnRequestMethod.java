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

package org.apache.james.jmap;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.function.Predicate;

public class BypassAuthOnRequestMethod implements Filter {

    private final AuthenticationFilter authenticationFilter;
    private final List<Predicate<HttpServletRequest>> listOfReasonsToBypassAuth;

    BypassAuthOnRequestMethod(AuthenticationFilter authenticationFilter, List<Predicate<HttpServletRequest>> listOfReasonsToBypassAuth) {

        this.authenticationFilter = authenticationFilter;
        this.listOfReasonsToBypassAuth = listOfReasonsToBypassAuth;
    }

    public static Builder bypass(AuthenticationFilter authenticationFilter) {
        return new Builder(authenticationFilter);
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest)request;

        listOfReasonsToBypassAuth.stream()
                .filter(r -> r.test(httpRequest))
                .map(x -> bypassAuth(request, response, chain))
                .findAny()
                .orElseGet(() -> continueWith(httpRequest, response, chain));
    }

    @Override
    public void destroy() {

    }

    private boolean bypassAuth(ServletRequest request, ServletResponse response, FilterChain chain) {
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException e) {
            throw Throwables.propagate(e);
        } finally {
            return false;
        }

    }

    private boolean continueWith(HttpServletRequest httpRequest, ServletResponse response, FilterChain chain) {
        try {
            authenticationFilter.doFilter(httpRequest, response, chain);
        } catch (IOException | ServletException e) {
            throw Throwables.propagate(e);
        } finally {
            return false;
        }
    }


    public static class Builder {
        private ImmutableList.Builder<Predicate<HttpServletRequest>> reasons = new ImmutableList.Builder<>();
        private final AuthenticationFilter authenticationFilter;

        private Builder(AuthenticationFilter authenticationFilter) {
            this.authenticationFilter = authenticationFilter;
        }

        public InitializedBuilder on(String requestMethod) {
            Preconditions.checkArgument(! Strings.isNullOrEmpty(requestMethod), "'requestMethod' is mandatory");
            reasons.add(r -> r.getMethod().equalsIgnoreCase(requestMethod.trim()));
            return new InitializedBuilder(this);
        }

        public BypassAuthOnRequestMethod only() {
            return new BypassAuthOnRequestMethod(this.authenticationFilter, this.reasons.build());
        }
    }

    public static class InitializedBuilder {
        private final Builder builder;

        public InitializedBuilder(Builder builder) {
            this.builder = builder;
        }

        public InitializedBuilder and(String requestMethod) {
            return builder.on(requestMethod);
        }

        public BypassAuthOnRequestMethod only() {
            return builder.only();
        }

    }
}
