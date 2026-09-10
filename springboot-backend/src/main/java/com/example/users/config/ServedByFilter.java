package com.example.users.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Stamps every response with the implementation that produced it, so the UI can
 * show which backend actually answered rather than which one it believes is
 * active. Without this, a misrouted request looks identical to a correct one.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ServedByFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Served-By";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader(HEADER, "springboot");
        chain.doFilter(request, response);
    }
}
