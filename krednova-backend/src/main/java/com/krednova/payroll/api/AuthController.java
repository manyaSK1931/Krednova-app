package com.krednova.payroll.api;

import com.krednova.payroll.security.AppUserDetailsService.P;
import com.krednova.payroll.security.PasswordPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    public record LoginReq(@NotBlank @Email @Size(max = 190) String email, @NotBlank @Size(max = 128) String password) {}
    public record UserDto(String name, String role, String employeeId, String email, boolean mustChangePassword, long id) {}
    public record ChangeReq(@NotBlank @Size(max = 128) String currentPassword, @NotBlank @Size(min = 12, max = 128) String newPassword) {}
    public record Lock(LocalDateTime lockedUntil) {}

    private final AuthenticationManager authManager;
    private final JdbcClient jdbc;
    private final PasswordEncoder encoder;
    private final Audit audit;
    private final SecurityContextRepository contextRepo = new HttpSessionSecurityContextRepository();
    private static final String BAD_LOGIN = "Email or password is incorrect, or the account is temporarily locked.";

    public AuthController(AuthenticationManager authManager, JdbcClient jdbc, PasswordEncoder encoder, Audit audit) {
        this.authManager = authManager; this.jdbc = jdbc; this.encoder = encoder; this.audit = audit;
    }

    /** Any GET sets the CSRF cookie; the React app calls this before signing in. */
    @GetMapping("/csrf")
    ResponseEntity<Void> csrf() { return ResponseEntity.noContent().build(); }

    @PostMapping("/login")
    UserDto login(@Valid @RequestBody LoginReq r, HttpServletRequest req, HttpServletResponse res) {
        String email = r.email().toLowerCase();
        // Unlock accounts whose lock has expired, then refuse locked accounts.
        jdbc.sql("update users set failed_attempts = 0, locked_until = null where email = :e and locked_until < :now")
            .param("e", email).param("now", LocalDateTime.now()).update();
        var lock = jdbc.sql("select locked_until from users where email = :e").param("e", email).query(Lock.class).optional();
        if (lock.isPresent() && lock.get().lockedUntil() != null)
            // Same answer as a wrong password, so nobody can use lockouts to find out which emails exist.
            throw new ApiException(HttpStatus.UNAUTHORIZED, BAD_LOGIN);

        Authentication auth;
        try {
            auth = authManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, r.password()));
        } catch (AuthenticationException ex) {
            jdbc.sql("update users set failed_attempts = failed_attempts + 1 where email = :e").param("e", email).update();
            jdbc.sql("update users set locked_until = :u where email = :e and failed_attempts >= 5")
                .param("u", LocalDateTime.now().plusMinutes(15)).param("e", email).update();
            jdbc.sql("select employee_id from users where email = :e and locked_until is not null").param("e", email).query(Long.class).optional()
                .ifPresent(id -> audit.log(id, "LOGIN_LOCKED", "account locked for 15 minutes after repeated failed sign-ins"));
            // Same message whether the email exists or not.
            throw new ApiException(HttpStatus.UNAUTHORIZED, BAD_LOGIN);
        }
        jdbc.sql("update users set failed_attempts = 0, locked_until = null where email = :e").param("e", email).update();

        if (req.getSession(false) != null) req.changeSessionId(); // prevents session fixation
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        req.getSession(true);
        contextRepo.saveContext(ctx, req, res);
        P p = (P) auth.getPrincipal();
        audit.log(p.employeeId(), "LOGIN", "signed in");
        return dto(p);
    }

    /** Lets anyone change their own password. All other sessions of that account stop working, and so does this one: sign in again. */
    @PostMapping("/change-password")
    ResponseEntity<Void> changePassword(@Valid @RequestBody ChangeReq r, @AuthenticationPrincipal P me,
                                        HttpServletRequest req, HttpServletResponse res, Authentication auth) {
        String hash = jdbc.sql("select password_hash from users where email = :e").param("e", me.getUsername()).query(String.class).single();
        if (!encoder.matches(r.currentPassword(), hash)) throw new ApiException(HttpStatus.BAD_REQUEST, "Your current password is incorrect.");
        if (encoder.matches(r.newPassword(), hash)) throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a password you have not used before.");
        String problem = PasswordPolicy.check(r.newPassword(), me.getUsername(), me.fullName());
        if (problem != null) throw new ApiException(HttpStatus.BAD_REQUEST, problem);
        jdbc.sql("update users set password_hash = :h, must_change_password = false, session_version = session_version + 1 where email = :e")
            .param("h", encoder.encode(r.newPassword())).param("e", me.getUsername()).update();
        audit.log(me.employeeId(), "PASSWORD_CHANGED", "own password changed");
        new SecurityContextLogoutHandler().logout(req, res, auth);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    UserDto me(@AuthenticationPrincipal P me) { return dto(me); }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest req, HttpServletResponse res, Authentication auth) {
        new SecurityContextLogoutHandler().logout(req, res, auth);
        return ResponseEntity.noContent().build();
    }

    private static UserDto dto(P p) { return new UserDto(p.fullName(), p.role(), p.employeeCode(), p.getUsername(), p.mustChangePassword(), p.employeeId()); }
}
