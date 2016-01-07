package org.apache.james.jmap;

import com.google.common.collect.Iterators;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

import javax.servlet.*;
import java.io.IOException;
import java.util.*;

public class CORSWhitelistFilter extends CrossOriginFilter {

     private static final Logger LOG = Log.getLogger(CORSWhitelistFilter.class);
    private final Filter decoratedFilter;

    public CORSWhitelistFilter(Filter originalFilter) {
        this.decoratedFilter = originalFilter;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        decoratedFilter.init(new CORSFilterConfig(filterConfig));
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        LOG.info("applying CrossOriginFilter filter");
        super.doFilter(request, response, new NullFilterChain());
        LOG.info("calling next filter ... ");
        decoratedFilter.doFilter(request, response, chain);
    }

    @Override
    public void destroy() {
        decoratedFilter.destroy();
    }


    public static class NullFilterChain implements FilterChain {

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
            return;
        }
    }

    public static class CORSFilterConfig implements FilterConfig {

        private final String filterName;
        private final ServletContext servletContext;
        // private Enumeration<String> initParameterNames;
        private final Map<String, String> initParameters;

        private CORSFilterConfig(String filterName, ServletContext servletContext) {
            this.filterName = filterName;
            this.servletContext = servletContext;
            this.initParameters = new HashMap<>();
            // this.initParameterNames = Iterators.asEnumeration(initParameters.keySet().iterator());
        }

        public CORSFilterConfig(FilterConfig filterConfig) {

            this(filterConfig.getFilterName(), filterConfig.getServletContext());

            Enumeration<String> paramIterator = filterConfig.getInitParameterNames();
            while(paramIterator.hasMoreElements()) {
                String name = paramIterator.nextElement();
                String value = filterConfig.getInitParameter(name);
                setInitParameter(name, value);
            }

            setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, "*");
            setInitParameter(CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, "*");
            setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, "GET,POST,HEAD");
            setInitParameter(CrossOriginFilter.ALLOWED_HEADERS_PARAM, "X-Requested-With,Content-Type,Accept,Origin");
        }

        @Override
        public String getFilterName() {
            return filterName;
        }

        @Override
        public ServletContext getServletContext() {
            return servletContext;
        }

        @Override
        public String getInitParameter(String name) {
            if (name == null || Objects.equals(name.trim(), "") || !initParameters.containsKey(name))
                return null;
            return initParameters.get(name);
        }

        @Override
        public Enumeration<String> getInitParameterNames() {
            // return initParameterNames;
            return Iterators.asEnumeration(initParameters.keySet().iterator());
        }

        public void setInitParameter(String name, String value) {
            initParameters.putIfAbsent(name, value);
        }


    }
}
