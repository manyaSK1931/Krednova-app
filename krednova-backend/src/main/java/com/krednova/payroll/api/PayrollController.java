package com.krednova.payroll.api;

import com.krednova.payroll.security.AppUserDetailsService.P;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payroll")
public class PayrollController {
    public record NewRun(@NotBlank @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])") String period) {}
    public record Run(Long id, String period, String status, Long createdBy, Long approvedBy, LocalDateTime createdAt) {}
    public record Rec(Long id, Long employeeId, String employeeName, BigDecimal basicSalary, BigDecimal hra, BigDecimal otherAllowances,
                      BigDecimal grossPay, BigDecimal pf, Integer lopDays, BigDecimal lopAmount, BigDecimal totalDeductions, BigDecimal netPay) {}
    public record Slip(String period, BigDecimal basicSalary, BigDecimal hra, BigDecimal otherAllowances, BigDecimal grossPay, BigDecimal pf,
                       Integer lopDays, BigDecimal lopAmount, BigDecimal totalDeductions, BigDecimal netPay) {}
    public record Sal(Long id, BigDecimal basicSalary, BigDecimal hra, BigDecimal otherAllowances) {}
    public record Span(LocalDate startDate, LocalDate endDate) {}
    public record Sum(String period, String status, BigDecimal net, Long headcount) {}

    private static final String RUN = "select id, period, status, created_by, approved_by, created_at from payroll_runs ";
    private final JdbcClient jdbc; private final Audit audit;
    public PayrollController(JdbcClient jdbc, Audit audit) { this.jdbc = jdbc; this.audit = audit; }

    @PostMapping("/runs")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    ResponseEntity<Run> create(@Valid @RequestBody NewRun r, @AuthenticationPrincipal P me) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.sql("insert into payroll_runs (period, status, created_by, created_at) values (:p, 'CALCULATED', :u, :t)")
            .param("p", r.period()).param("u", me.employeeId()).param("t", LocalDateTime.now()).update(kh, "id"); // a duplicate period returns 409
        long runId = kh.getKey().longValue();
        calculate(runId, YearMonth.parse(r.period()));
        audit.log(me.employeeId(), "PAYROLL_CALCULATED", "run " + runId + " for " + r.period());
        return ResponseEntity.status(HttpStatus.CREATED).body(jdbc.sql(RUN + "where id = :id").param("id", runId).query(Run.class).single());
    }

    /** Illustrative rules: PF = 12% of basic, unpaid leave = gross / 26 per day. Replace with your company policy and statutory rules. */
    private void calculate(long runId, YearMonth ym) {
        LocalDate first = ym.atDay(1), last = ym.atEndOfMonth();
        var staff = jdbc.sql("select id, basic_salary, hra, other_allowances from employees where status = 'ACTIVE' and joining_date <= :last")
            .param("last", last).query(Sal.class).list();
        for (Sal s : staff) {
            int lop = 0;
            var spans = jdbc.sql("select start_date, end_date from leave_requests where employee_id = :e and leave_type = 'UNPAID' and status = 'APPROVED' and start_date <= :last and end_date >= :first")
                .param("e", s.id()).param("first", first).param("last", last).query(Span.class).list();
            for (Span sp : spans) {
                LocalDate from = sp.startDate().isBefore(first) ? first : sp.startDate(), to = sp.endDate().isAfter(last) ? last : sp.endDate();
                lop += (int) Math.max(0, ChronoUnit.DAYS.between(from, to) + 1);
            }
            BigDecimal gross = s.basicSalary().add(s.hra()).add(s.otherAllowances());
            BigDecimal pf = s.basicSalary().multiply(new BigDecimal("0.12")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lopAmt = gross.divide(new BigDecimal("26"), 2, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(lop)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal ded = pf.add(lopAmt);
            jdbc.sql("insert into payroll_records (run_id, employee_id, basic_salary, hra, other_allowances, gross_pay, pf, lop_days, lop_amount, total_deductions, net_pay) "
                    + "values (:r, :e, :b, :h, :o, :g, :pf, :l, :la, :d, :n)")
                .param("r", runId).param("e", s.id()).param("b", s.basicSalary()).param("h", s.hra()).param("o", s.otherAllowances()).param("g", gross)
                .param("pf", pf).param("l", lop).param("la", lopAmt).param("d", ded).param("n", gross.subtract(ded)).update();
        }
    }

    @GetMapping("/runs")
    @PreAuthorize("hasRole('ADMIN')")
    List<Run> runs() { return jdbc.sql(RUN + "order by period desc").query(Run.class).list(); }

    @GetMapping("/runs/{id}/records")
    @PreAuthorize("hasRole('ADMIN')")
    List<Rec> records(@PathVariable long id) {
        return jdbc.sql("select r.id, r.employee_id, e.full_name as employee_name, r.basic_salary, r.hra, r.other_allowances, r.gross_pay, r.pf, r.lop_days, r.lop_amount, r.total_deductions, r.net_pay "
                + "from payroll_records r join employees e on e.id = r.employee_id where r.run_id = :id order by e.employee_code").param("id", id).query(Rec.class).list();
    }

    @PostMapping("/runs/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    ResponseEntity<Void> approve(@PathVariable long id, @AuthenticationPrincipal P me) {
        Run run = jdbc.sql(RUN + "where id = :id").param("id", id).query(Run.class).optional()
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payroll run not found."));
        if (run.createdBy() != null && run.createdBy() == me.employeeId()) { // two-person rule, once there is a second admin
            long admins = jdbc.sql("select count(*) from users u join employees e on e.id = u.employee_id where u.role = 'ADMIN' and u.enabled = true and e.status = 'ACTIVE'")
                .query(Long.class).single();
            if (admins > 1) throw new ApiException(HttpStatus.FORBIDDEN, "A different admin must approve a payroll run that you calculated.");
        }
        int n = jdbc.sql("update payroll_runs set status = 'APPROVED', approved_by = :u where id = :id and status = 'CALCULATED'").param("u", me.employeeId()).param("id", id).update();
        if (n == 0) throw new ApiException(HttpStatus.CONFLICT, "Only a calculated payroll run can be approved.");
        audit.log(me.employeeId(), "PAYROLL_APPROVED", "run " + id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/runs/{id}/finalize")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    ResponseEntity<Void> finalizeRun(@PathVariable long id, @AuthenticationPrincipal P me) {
        int n = jdbc.sql("update payroll_runs set status = 'FINALIZED' where id = :id and status = 'APPROVED'").param("id", id).update();
        if (n == 0) throw new ApiException(HttpStatus.CONFLICT, "Only an approved payroll run can be finalized.");
        audit.log(me.employeeId(), "PAYROLL_FINALIZED", "run " + id);
        return ResponseEntity.noContent().build();
    }

    /** Net payroll per month (latest 6), for the admin dashboard. */
    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN')")
    List<Sum> summary() {
        return jdbc.sql("select p.period, p.status, coalesce(sum(r.net_pay), 0) as net, count(r.id) as headcount "
                + "from payroll_runs p left join payroll_records r on r.run_id = p.id group by p.id, p.period, p.status order by p.period desc limit 6")
            .query(Sum.class).list();
    }

    /** Your own finalized payslips. The employee comes from the session, so nobody can ask for someone else's. */
    @GetMapping("/me")
    List<Slip> mine(@AuthenticationPrincipal P me) {
        return jdbc.sql("select p.period, r.basic_salary, r.hra, r.other_allowances, r.gross_pay, r.pf, r.lop_days, r.lop_amount, r.total_deductions, r.net_pay "
                + "from payroll_records r join payroll_runs p on p.id = r.run_id where r.employee_id = :e and p.status = 'FINALIZED' order by p.period desc")
            .param("e", me.employeeId()).query(Slip.class).list();
    }
}
