package com.krednova.payroll.api;

import com.krednova.payroll.security.AppUserDetailsService.P;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/leave-requests")
public class LeaveController {
    public record NewLeave(@NotBlank @Pattern(regexp = "CASUAL|SICK|EARNED|UNPAID") String leaveType, @NotNull LocalDate startDate,
                           @NotNull LocalDate endDate, @NotBlank @Size(max = 200) String reason) {}
    public record Decision(@NotBlank @Pattern(regexp = "APPROVED|REJECTED") String decision) {}
    public record Leave(Long id, Long employeeId, String employeeName, String leaveType, LocalDate startDate, LocalDate endDate, Integer days, String reason, String status) {}
    public record Bal(String leaveType, Integer daysLeft) {}
    public record Lr(Long id, Long employeeId, String leaveType, Integer days, String status, Long managerId) {}

    private final JdbcClient jdbc; private final Audit audit;
    public LeaveController(JdbcClient jdbc, Audit audit) { this.jdbc = jdbc; this.audit = audit; }

    @PostMapping
    @Transactional
    ResponseEntity<Void> apply(@Valid @RequestBody NewLeave r, @AuthenticationPrincipal P me) {
        if (r.endDate().isBefore(r.startDate())) throw new ApiException(HttpStatus.BAD_REQUEST, "The end date cannot be before the start date.");
        int days = (int) ChronoUnit.DAYS.between(r.startDate(), r.endDate()) + 1;
        if (days > 60) throw new ApiException(HttpStatus.BAD_REQUEST, "A single request can cover at most 60 days.");
        int overlap = jdbc.sql("select count(*) from leave_requests where employee_id = :e and status in ('PENDING','APPROVED') and start_date <= :end and end_date >= :start")
            .param("e", me.employeeId()).param("start", r.startDate()).param("end", r.endDate()).query(Integer.class).single();
        if (overlap > 0) throw new ApiException(HttpStatus.CONFLICT, "You already have a leave request for these dates.");
        if (!r.leaveType().equals("UNPAID")) {
            int left = jdbc.sql("select days_left from leave_balances where employee_id = :e and leave_type = :t")
                .param("e", me.employeeId()).param("t", r.leaveType()).query(Integer.class).optional().orElse(0);
            if (days > left) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Not enough leave balance. You have " + left + " day(s) left.");
        }
        jdbc.sql("insert into leave_requests (employee_id, leave_type, start_date, end_date, days, reason) values (:e, :t, :s, :n, :d, :r)")
            .param("e", me.employeeId()).param("t", r.leaveType()).param("s", r.startDate()).param("n", r.endDate()).param("d", days).param("r", r.reason().trim()).update();
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping
    List<Leave> list(@AuthenticationPrincipal P me) {
        return jdbc.sql("select l.id, l.employee_id, e.full_name as employee_name, l.leave_type, l.start_date, l.end_date, l.days, l.reason, l.status "
                + "from leave_requests l join employees e on e.id = l.employee_id where " + EmployeeController.scope(me) + " order by l.start_date desc, l.id desc")
            .param("me", me.employeeId()).query(Leave.class).list();
    }

    @GetMapping("/balances")
    List<Bal> balances(@AuthenticationPrincipal P me) {
        return jdbc.sql("select leave_type, days_left from leave_balances where employee_id = :e order by leave_type").param("e", me.employeeId()).query(Bal.class).list();
    }

    @PatchMapping("/{id}/decision")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    ResponseEntity<Void> decide(@PathVariable long id, @Valid @RequestBody Decision d, @AuthenticationPrincipal P me) {
        Lr lr = jdbc.sql("select l.id, l.employee_id, l.leave_type, l.days, l.status, e.manager_id from leave_requests l join employees e on e.id = l.employee_id where l.id = :id")
            .param("id", id).query(Lr.class).optional().orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Leave request not found."));
        if (lr.employeeId() == me.employeeId()) throw new ApiException(HttpStatus.FORBIDDEN, "You cannot decide your own request.");
        if (me.role().equals("MANAGER") && (lr.managerId() == null || lr.managerId() != me.employeeId())) throw new ApiException(HttpStatus.FORBIDDEN, "You can only decide requests from your own team.");
        int n = jdbc.sql("update leave_requests set status = :s, reviewed_by = :by, reviewed_at = :at where id = :id and status = 'PENDING'")
            .param("s", d.decision()).param("by", me.employeeId()).param("at", LocalDateTime.now()).param("id", id).update();
        if (n == 0) throw new ApiException(HttpStatus.CONFLICT, "This request has already been decided.");
        if (d.decision().equals("APPROVED") && !lr.leaveType().equals("UNPAID")) {
            int u = jdbc.sql("update leave_balances set days_left = days_left - :d where employee_id = :e and leave_type = :t and days_left >= :d")
                .param("d", lr.days()).param("e", lr.employeeId()).param("t", lr.leaveType()).update();
            if (u == 0) throw new ApiException(HttpStatus.CONFLICT, "The employee no longer has enough balance.");
        }
        audit.log(me.employeeId(), "LEAVE_" + d.decision(), "leave request " + id);
        return ResponseEntity.noContent().build();
    }
}
