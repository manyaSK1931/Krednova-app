package com.krednova.payroll.api;

import com.krednova.payroll.config.SettingsService;
import com.krednova.payroll.security.AppUserDetailsService.P;
import com.krednova.payroll.security.PasswordPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/employees")
public class EmployeeController {
    public record Emp(Long id, String employeeCode, String fullName, String email, String department, String jobTitle, Long managerId,
                      LocalDate joiningDate, String status, BigDecimal basicSalary, BigDecimal hra, BigDecimal otherAllowances) {
        Emp withoutSalary() { return new Emp(id, employeeCode, fullName, email, department, jobTitle, managerId, joiningDate, status, null, null, null); }
    }
    public record NewEmp(@NotBlank @Size(max = 100) String fullName, @NotBlank @Email @Size(max = 190) String email,
                         @NotBlank @Size(max = 60) String department, @NotBlank @Size(max = 80) String jobTitle,
                         @NotNull @PositiveOrZero BigDecimal basicSalary, Long managerId,
                         @NotBlank @Pattern(regexp = "ADMIN|MANAGER|EMPLOYEE") String role,
                         @NotBlank @Size(min = 12, max = 128) String temporaryPassword) {}
    public record StatusReq(@NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String status) {}
    public record ResetReq(@NotBlank @Size(min = 12, max = 128) String temporaryPassword) {}
    public record Who(String email, String fullName) {}

    private static final String SELECT = "select id, employee_code, full_name, email, department, job_title, manager_id, joining_date, "
            + "status, basic_salary, hra, other_allowances from employees e where ";

    private final JdbcClient jdbc; private final PasswordEncoder encoder; private final Audit audit; private final SettingsService settings;
    public EmployeeController(JdbcClient jdbc, PasswordEncoder encoder, Audit audit, SettingsService settings) { this.jdbc = jdbc; this.encoder = encoder; this.audit = audit; this.settings = settings; }

    /** Which employee rows this user may see. Enforced here, not in React. */
    static String scope(P me) {
        return switch (me.role()) {
            case "ADMIN" -> "1 = 1";
            case "MANAGER" -> "(e.id = :me or e.manager_id = :me)";
            default -> "e.id = :me";
        };
    }

    @GetMapping
    List<Emp> list(@AuthenticationPrincipal P me) {
        var rows = jdbc.sql(SELECT + scope(me) + " order by e.employee_code").param("me", me.employeeId()).query(Emp.class).list();
        return me.role().equals("ADMIN") ? rows : rows.stream().map(Emp::withoutSalary).toList();
    }

    @GetMapping("/{id}")
    Emp one(@PathVariable long id, @AuthenticationPrincipal P me) {
        Emp e = jdbc.sql(SELECT + "(" + scope(me) + ") and e.id = :id").param("me", me.employeeId()).param("id", id).query(Emp.class).optional()
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Employee not found."));
        return me.role().equals("ADMIN") ? e : e.withoutSalary();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    ResponseEntity<Emp> create(@Valid @RequestBody NewEmp r, @AuthenticationPrincipal P me) {
        String problem = PasswordPolicy.check(r.temporaryPassword(), r.email(), r.fullName());
        if (problem != null) throw new ApiException(HttpStatus.BAD_REQUEST, problem);
        BigDecimal basic = r.basicSalary();
        KeyHolder kh = new GeneratedKeyHolder();
        // Illustrative defaults: HRA 40% and other allowances 10% of basic.
        var st = jdbc.sql("insert into employees (employee_code, full_name, email, department, job_title, manager_id, joining_date, status, basic_salary, hra, other_allowances) "
                + "values (:code, :n, :em, :d, :t, :m, :j, 'ACTIVE', :b, :h, :o)")
            .param("code", "TMP-" + java.util.UUID.randomUUID().toString().substring(0, 8)).param("n", r.fullName().trim()).param("em", r.email().toLowerCase())
            .param("d", r.department().trim()).param("t", r.jobTitle().trim()).param("m", r.managerId(), Types.BIGINT)
            .param("j", LocalDate.now()).param("b", basic).param("h", basic.multiply(new BigDecimal("0.40"))).param("o", basic.multiply(new BigDecimal("0.10")));
        st.update(kh, "id");
        long id = kh.getKey().longValue();
        jdbc.sql("update employees set employee_code = :c where id = :id").param("c", String.format("KN-%03d", id)).param("id", id).update();
        jdbc.sql("insert into users (email, password_hash, role, employee_id, must_change_password) values (:e, :p, :r, :id, true)")
            .param("e", r.email().toLowerCase()).param("p", encoder.encode(r.temporaryPassword())).param("r", r.role()).param("id", id).update();
        var cfg = settings.load();
        for (var b : new String[][] {{"CASUAL", String.valueOf(cfg.casualDays())}, {"SICK", String.valueOf(cfg.sickDays())}, {"EARNED", String.valueOf(cfg.earnedDays())}})
            jdbc.sql("insert into leave_balances (employee_id, leave_type, days_left) values (:id, :t, :d)").param("id", id).param("t", b[0]).param("d", Integer.parseInt(b[1])).update();
        audit.log(me.employeeId(), "EMPLOYEE_CREATED", "employee id " + id + " with role " + r.role());
        Emp created = jdbc.sql(SELECT + "e.id = :id").param("id", id).query(Emp.class).single();
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    ResponseEntity<Void> status(@PathVariable long id, @Valid @RequestBody StatusReq r, @AuthenticationPrincipal P me) {
        if (id == me.employeeId()) throw new ApiException(HttpStatus.CONFLICT, "You cannot change your own status.");
        int n = jdbc.sql("update employees set status = :s where id = :id").param("s", r.status()).param("id", id).update();
        if (n == 0) throw new ApiException(HttpStatus.NOT_FOUND, "Employee not found.");
        jdbc.sql("update users set enabled = :en, session_version = session_version + 1 where employee_id = :id").param("en", r.status().equals("ACTIVE")).param("id", id).update();
        audit.log(me.employeeId(), "EMPLOYEE_STATUS", "employee id " + id + " set to " + r.status());
        return ResponseEntity.noContent().build();
    }

    /** Admin sets a new temporary password (for someone who forgot theirs). They must choose their own at next sign-in. */
    @PatchMapping("/{id}/password")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    ResponseEntity<Void> resetPassword(@PathVariable long id, @Valid @RequestBody ResetReq r, @AuthenticationPrincipal P me) {
        if (id == me.employeeId()) throw new ApiException(HttpStatus.CONFLICT, "To change your own password, use Change password in the top bar.");
        Who who = jdbc.sql("select email, full_name from employees where id = :id").param("id", id).query(Who.class).optional()
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Employee not found."));
        String problem = PasswordPolicy.check(r.temporaryPassword(), who.email(), who.fullName());
        if (problem != null) throw new ApiException(HttpStatus.BAD_REQUEST, problem);
        int n = jdbc.sql("update users set password_hash = :h, must_change_password = true, session_version = session_version + 1, failed_attempts = 0, locked_until = null where employee_id = :id")
            .param("h", encoder.encode(r.temporaryPassword())).param("id", id).update();
        if (n == 0) throw new ApiException(HttpStatus.NOT_FOUND, "That employee has no login account.");
        audit.log(me.employeeId(), "PASSWORD_RESET", "employee id " + id);
        return ResponseEntity.noContent().build();
    }
}
