package com.krednova.payroll.security;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Loads login accounts from MySQL. The logged-in user is always taken from here, never from request data. */
@Service
public class AppUserDetailsService implements UserDetailsService {

    /** The signed-in user stored in the session. */
    public static class P extends User {
        private final long employeeId;
        private final String employeeCode, fullName, role;
        private final int sessionVersion; private final boolean mustChangePassword;

        public P(String email, String hash, boolean enabled, String role, long employeeId, String code, String name, int sessionVersion, boolean mustChangePassword) {
            super(email, hash, enabled, true, true, true, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
            this.employeeId = employeeId; this.employeeCode = code; this.fullName = name; this.role = role;
            this.sessionVersion = sessionVersion; this.mustChangePassword = mustChangePassword;
        }
        public long employeeId() { return employeeId; }
        public String employeeCode() { return employeeCode; }
        public String fullName() { return fullName; }
        public String role() { return role; }
        public int sessionVersion() { return sessionVersion; }
        public boolean mustChangePassword() { return mustChangePassword; }
    }

    public record Row(String email, String passwordHash, boolean enabled, String role, long employeeId, String employeeCode, String fullName, int sessionVersion, boolean mustChangePassword) {}

    private final JdbcClient jdbc;
    public AppUserDetailsService(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public UserDetails loadUserByUsername(String email) {
        return jdbc.sql("select u.email, u.password_hash, u.enabled, u.role, u.employee_id, e.employee_code, e.full_name, u.session_version, u.must_change_password "
                + "from users u join employees e on e.id = u.employee_id where u.email = :e and e.status = 'ACTIVE'")
            .param("e", email.toLowerCase()).query(Row.class).optional()
            .map(r -> (UserDetails) new P(r.email(), r.passwordHash(), r.enabled(), r.role(), r.employeeId(), r.employeeCode(), r.fullName(), r.sessionVersion(), r.mustChangePassword()))
            .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    }
}
