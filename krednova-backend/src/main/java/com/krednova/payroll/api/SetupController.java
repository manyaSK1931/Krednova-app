package com.krednova.payroll.api;

import com.krednova.payroll.config.SettingsService;
import com.krednova.payroll.security.PasswordPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

/**
 * First-run setup: lets the very first person create the administrator account.
 * It only works while the users table is empty. After that it always answers 409.
 * Optional extra protection: set the environment variable KREDNOVA_SETUP_KEY and the key must be entered too.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class SetupController {
    public record Status(boolean needsSetup, boolean requiresKey) {}
    public record SetupReq(@NotBlank @Size(max = 100) String fullName,
                           @NotBlank @Email @Size(max = 190) String email,
                           @NotBlank @Size(min = 12, max = 128) String password,
                           @Size(max = 128) String setupKey) {}

    private static final Object LOCK = new Object();
    private final JdbcClient jdbc;
    private final PasswordEncoder encoder;
    private final Audit audit;
    private final TransactionTemplate tx;
    private final SettingsService settings;

    public SetupController(JdbcClient jdbc, PasswordEncoder encoder, Audit audit, PlatformTransactionManager tm, SettingsService settings) {
        this.settings = settings; this.jdbc = jdbc; this.encoder = encoder; this.audit = audit; this.tx = new TransactionTemplate(tm);
    }

    private static String requiredKey() {
        String k = System.getenv("KREDNOVA_SETUP_KEY");
        return (k == null || k.isBlank()) ? null : k;
    }

    private boolean noUsers() {
        return jdbc.sql("select count(*) from users").query(Long.class).single() == 0L;
    }

    @GetMapping("/setup-status")
    Status status() { return new Status(noUsers(), requiredKey() != null); }

    @PostMapping("/setup")
    @ResponseStatus(HttpStatus.CREATED)
    void setup(@Valid @RequestBody SetupReq r) {
        String key = requiredKey();
        if (key != null) {
            String given = r.setupKey() == null ? "" : r.setupKey();
            if (!MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8)))
                throw new ApiException(HttpStatus.FORBIDDEN, "Setup key is incorrect.");
        }
        String problem = PasswordPolicy.check(r.password(), r.email(), r.fullName());
        if (problem != null) throw new ApiException(HttpStatus.BAD_REQUEST, problem);
        synchronized (LOCK) { // the check and the insert finish (and commit) before another request can enter
            tx.executeWithoutResult(s -> {
                if (!noUsers()) throw new ApiException(HttpStatus.CONFLICT, "Setup is already done. Please sign in.");
                String email = r.email().trim().toLowerCase();
                var kh = new GeneratedKeyHolder();
                jdbc.sql("insert into employees (employee_code, full_name, email, department, job_title, joining_date, status, basic_salary, hra, other_allowances) "
                        + "values ('KN-001', :n, :e, 'HR', 'Administrator', :j, 'ACTIVE', 0, 0, 0)")
                    .param("n", r.fullName().trim()).param("e", email).param("j", LocalDate.now()).update(kh, "id");
                long id = kh.getKey().longValue();
                jdbc.sql("insert into users (email, password_hash, role, employee_id) values (:e, :p, 'ADMIN', :id)")
                    .param("e", email).param("p", encoder.encode(r.password())).param("id", id).update();
                var cfg = settings.load();
        for (var b : new String[][] {{"CASUAL", String.valueOf(cfg.casualDays())}, {"SICK", String.valueOf(cfg.sickDays())}, {"EARNED", String.valueOf(cfg.earnedDays())}})
                    jdbc.sql("insert into leave_balances (employee_id, leave_type, days_left) values (:id, :t, :d)")
                        .param("id", id).param("t", b[0]).param("d", Integer.parseInt(b[1])).update();
                audit.log(id, "SETUP", "First administrator account created");
            });
        }
    }
}
