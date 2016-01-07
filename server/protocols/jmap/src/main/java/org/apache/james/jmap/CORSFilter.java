package org.apache.james.jmap;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

public class CORSFilter implements Filter {

    private final Filter nestedFilter;

    public CORSFilter(Filter nestedFilter) {
        this.nestedFilter = nestedFilter;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

        System.out.println("WTF ");
        HttpServletResponse servletResponse = (HttpServletResponse) response;
        servletResponse.addHeader("Access-Control-Allow-Origin", "*");
        servletResponse.addHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT");
        servletResponse.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, Accept");
        nestedFilter.doFilter(request, response, chain);
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void destroy() { }
}
