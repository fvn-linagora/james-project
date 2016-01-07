package org.apache.james.jmap;

import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

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
        servletResponse.addHeader("Access-Control-Allow-Headers", "Content-Type");
        nestedFilter.doFilter(request, response, chain);
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void destroy() { }
}