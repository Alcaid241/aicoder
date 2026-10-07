package com.ai.coder.mcp.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

import java.io.IOException;

/**
 * MCP 端点鉴权：仅对 /mcp（及 /api/mcp）校验 X-Mcp-Token == mcp.access-token。
 * 其他路径（swagger 等）放行，交由各自机制。Gateway 已对 /api/mcp/** 放行 JWT。
 */
@Configuration
@RequiredArgsConstructor
public class McpAuthFilter {

    private final McpProperties props;

    @Bean
    public FilterRegistrationBean<Filter> mcpTokenFilterRegistration() {
        FilterRegistrationBean<Filter> reg = new FilterRegistrationBean<>();
        reg.setFilter(this::doFilter);
        reg.addUrlPatterns("/*");
        reg.setOrder(0);
        return reg;
    }

    /** REST 端点（/api/mcp/tools, /api/mcp/call）供内部服务调用，不走 MCP token 鉴权。 */
    private static boolean isRestEndpoint(String path) {
        return path != null && (path.endsWith("/tools") || path.contains("/call/"));
    }

    void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        String path = req.getRequestURI();
        if (path != null && (path.equals("/mcp") || path.startsWith("/mcp/") || path.startsWith("/api/mcp"))
                && !isRestEndpoint(path)) {
            String token = req.getHeader("X-Mcp-Token");
            String expected = props.getAccessToken();
            if (expected == null || expected.isBlank() || !expected.equals(token)) {
                resp.setStatus(HttpStatus.UNAUTHORIZED.value());
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
