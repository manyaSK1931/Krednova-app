package com.krednova.payroll.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Slows down password guessing: at most 10 sign-in style requests per minute from one IP address.
 * Kept in memory, which is fine for one server. Behind a reverse proxy, set server.forward-headers-strategy=native
 * so the real client IP is used (only do that if the proxy is the only way in).
 */
public class RateLimitFilter extends OncePerRequestFilter {
    private final int max;
    private static final long WINDOW_MS = 60_000;
    private static final Set<String> PATHS = Set.of("/api/v1/auth/login", "/api/v1/auth/setup", "/api/v1/auth/change-password");
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public RateLimitFilter(int max) { this.max = max; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        return !"POST".equalsIgnoreCase(req.getMethod()) || !PATHS.contains(req.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        long now = System.currentTimeMillis();
        Deque<Long> q = hits.computeIfAbsent(req.getRemoteAddr(), k -> new ArrayDeque<>());
        boolean blocked;
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > WINDOW_MS) q.pollFirst();
            blocked = q.size() >= max;
            if (!blocked) q.addLast(now);
        }
        if (hits.size() > 10_000) // keep memory bounded
            hits.entrySet().removeIf(e -> { synchronized (e.getValue()) { return e.getValue().isEmpty() || now - e.getValue().peekLast() > WINDOW_MS; } });
        if (blocked) {
            res.setStatus(429);
            res.setHeader("Retry-After", "60");
            res.setContentType("application/json");
            res.getOutputStream().write("{\"message\":\"Too many requests. Please wait a minute and try again.\"}".getBytes(StandardCharsets.UTF_8));
            return;
        }
        chain.doFilter(req, res);
    }
}
