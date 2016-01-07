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

package org.apache.james.http.jetty;

import java.io.Closeable;
import java.io.IOException;
import java.util.EnumSet;
import java.util.function.BiConsumer;

import javax.servlet.*;

import org.eclipse.jetty.server.HandlerContainer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.FilterMapping;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class JettyHttpServer implements Closeable {
    
    public static JettyHttpServer create(Configuration configuration) {
        return new JettyHttpServer(configuration);
    }

    private Server server;
    private ServerConnector serverConnector;
    private final Configuration configuration;

    private JettyHttpServer(Configuration configuration) {
        this.configuration = configuration;
        this.server = new Server();
        this.server.addConnector(buildServerConnector(configuration));
        this.server.setHandler(buildServletHandler(configuration));
    }

    private ServerConnector buildServerConnector(Configuration configuration) {
        this.serverConnector = new ServerConnector(server);
        configuration.getPort().ifPresent(serverConnector::setPort);
        return serverConnector;
    }

    private ServletHandler buildServletHandler(Configuration configuration) {
        ServletHandler servletHandler = new ServletHandler();

        ConfigureCORS(servletHandler);
        BiConsumer<String, ServletHolder> addServletMapping = (path, servletHolder) -> servletHandler.addServletWithMapping(servletHolder, path);
        BiConsumer<String, FilterHolder> addFilterMapping = (path, filterHolder) -> servletHandler.addFilterWithMapping(filterHolder, path, EnumSet.of(DispatcherType.REQUEST));
        Maps.transformEntries(configuration.getMappings(), this::toServletHolder).forEach(addServletMapping);
        Maps.transformEntries(configuration.getFilters(), this::toFilterHolder).forEach(addFilterMapping);

        return servletHandler;
    }

    private void ConfigureCORS(ServletHandler servletHandler) {
        // FilterHolder holder = new FilterHolder(CrossOriginFilter.class);
        FilterHolder holder = new FilterHolder(MyCrossOriginFilter.class);
        holder.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, "*");
        holder.setInitParameter(CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, "*");
        holder.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, "GET,POST,HEAD");
        holder.setInitParameter(CrossOriginFilter.ALLOWED_HEADERS_PARAM, "X-Requested-With,Content-Type,Accept,Origin");
        holder.setName("cross-origin");
//        FilterMapping fm = new FilterMapping();
//        fm.setFilterName("cross-origin");
//        fm.setPathSpec("*");
        // servletHandler.addFilter(holder, fm );
        servletHandler.addFilterWithMapping(holder, "/jmap", EnumSet.of(DispatcherType.REQUEST));
        servletHandler.addFilterWithMapping(holder, "/authentication", EnumSet.of(DispatcherType.REQUEST));
    }


    @SuppressWarnings("unchecked")
    private ServletHolder toServletHolder(String path, Object value) {
        if (value instanceof Servlet) {
            return new ServletHolder((Servlet) value);
        }
        return new ServletHolder((Class<? extends Servlet>)value);
    }
    
    @SuppressWarnings("unchecked")
    private FilterHolder toFilterHolder(String path, Object value) {
        if (value instanceof Filter) {
            return new FilterHolder((Filter)value);
        }
        return new FilterHolder((Class<? extends Filter>)value);
    }
    
    public JettyHttpServer start() throws Exception {
        server.start();
        return this;
    }
    
    public void stop() throws Exception {
        server.stop();
    }

    public int getPort() {
        return serverConnector.getLocalPort();
    }

    public Configuration getConfiguration() {
        return configuration;
    }
    
    @Override
    public void close() {
        try {
            stop();
        } catch (Exception e) {
            Throwables.propagate(e);
        }
    }

    public static class MyCrossOriginFilter extends CrossOriginFilter {

        private static final Logger LOG = Log.getLogger(MyCrossOriginFilter.class);

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
            // System.out.println("Youpi CORS ! ");
            LOG.info("MyCrossOriginFilter.doFilter passed !");
            super.doFilter(request, response, chain);
        }
    }
}
