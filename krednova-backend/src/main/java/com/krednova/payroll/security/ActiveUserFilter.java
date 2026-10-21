package com.krednova.payroll.security;

import com.krednova.payroll.security.AppUserDetailsService.P;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Checks on every request that the signed-in person is still allowed in. If the account was deactivated,
 * its role changed, or its password was changed or reset, the old session stops working immediately
 * (instead of staying valid until the 30-minute timeout).
 */
public class ActiveUserFilter extends OncePerRequestFilter {
    public record State(boolean enabled, String role, int sessionVersion, String status) {}

    private final JdbcClient jdbc;
    public ActiveUserFilter(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof P p) {
            var st = jdbc.sql("select u.enabled, u.role, u.session_version, e.status from users u join employees e on e.id = u.employee_id where u.email = :e")
                .param("e", p.getUsername()).query(State.class).optional();
            boolean ok = st.isPresent() && st.get().enabled() && "ACTIVE".equals(st.get().status())
                && st.get().role().equals(p.role()) && st.get().sessionVersion() == p.sessionVersion();
            if (!ok) {
                SecurityContextHolder.clearContext();
                HttpSession session = req.getSession(false);
                if (session != null) session.invalidate();
            }
        }
        chain.doFilter(req, res);
    }
}
