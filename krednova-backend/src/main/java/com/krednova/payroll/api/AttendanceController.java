package com.krednova.payroll.api;

import com.krednova.payroll.config.SettingsService;
import com.krednova.payroll.security.AppUserDetailsService.P;
import java.time.*;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/attendance")
public class AttendanceController {
    public record Att(Long id, LocalDate workDate, LocalDateTime checkIn, LocalDateTime checkOut, String status, Integer workedMinutes) {}
    public record Today(Long id, String fullName, String department, String status) {}

    public record DayCount(LocalDate workDate, Long people) {}

    private final JdbcClient jdbc;
    private final ZoneId zone; private final SettingsService settings;

    public AttendanceController(JdbcClient jdbc, @Value("${krednova.timezone}") String tz, SettingsService settings) {
        this.jdbc = jdbc; this.zone = ZoneId.of(tz); this.settings = settings;
    }

    /** The time always comes from the server clock. The browser cannot choose it. */
    @PostMapping("/check-in")
    @Transactional
    ResponseEntity<Void> checkIn(@AuthenticationPrincipal P me) {
        LocalDateTime now = LocalDateTime.now(zone);
        int existing = jdbc.sql("select count(*) from attendance where employee_id = :e and work_date = :d")
            .param("e", me.employeeId()).param("d", now.toLocalDate()).query(Integer.class).single();
        if (existing > 0) throw new ApiException(HttpStatus.CONFLICT, "You have already checked in today.");
        var cfg = settings.load(); // shift start and grace period are set by the admin in Settings
        String status = now.toLocalTime().isAfter(LocalTime.parse(cfg.shiftStart()).plusMinutes(cfg.graceMinutes())) ? "LATE" : "PRESENT";
        jdbc.sql("insert into attendance (employee_id, work_date, check_in, status) values (:e, :d, :t, :s)")
            .param("e", me.employeeId()).param("d", now.toLocalDate()).param("t", now).param("s", status).update();
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/check-out")
    @Transactional
    ResponseEntity<Void> checkOut(@AuthenticationPrincipal P me) {
        LocalDateTime now = LocalDateTime.now(zone);
        var open = jdbc.sql("select id, work_date, check_in, check_out, status, worked_minutes from attendance where employee_id = :e and work_date = :d and check_out is null")
            .param("e", me.employeeId()).param("d", now.toLocalDate()).query(Att.class).optional()
            .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "You have no open check-in to close."));
        int minutes = (int) Duration.between(open.checkIn(), now).toMinutes();
        jdbc.sql("update attendance set check_out = :t, worked_minutes = :m where id = :id").param("t", now).param("m", minutes).param("id", open.id()).update();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    List<Att> mine(@AuthenticationPrincipal P me) {
        return jdbc.sql("select id, work_date, check_in, check_out, status, worked_minutes from attendance where employee_id = :e order by work_date desc limit 31")
            .param("e", me.employeeId()).query(Att.class).list();
    }

    /** Today's status for everyone this manager or admin is allowed to see. */
    @GetMapping("/today")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    List<Today> today(@AuthenticationPrincipal P me) {
        return jdbc.sql("select e.id, e.full_name, e.department, case when a.id is not null then a.status "
                + "when exists (select 1 from leave_requests l where l.employee_id = e.id and l.status = 'APPROVED' and :d between l.start_date and l.end_date) then 'ON_LEAVE' "
                + "else 'NOT_MARKED' end as status from employees e left join attendance a on a.employee_id = e.id and a.work_date = :d "
                + "where e.status = 'ACTIVE' and " + EmployeeController.scope(me) + " order by e.employee_code")
            .param("d", LocalDate.now(zone)).param("me", me.employeeId()).query(Today.class).list();
    }

    /** How many people checked in on each of the last 7 days, for the people this manager or admin may see. */
    @GetMapping("/week")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    List<DayCount> week(@AuthenticationPrincipal P me) {
        LocalDate today = LocalDate.now(zone);
        return jdbc.sql("select a.work_date, count(*) as people from attendance a join employees e on e.id = a.employee_id "
                + "where a.work_date between :startD and :endD and " + EmployeeController.scope(me) + " group by a.work_date order by a.work_date")
            .param("startD", today.minusDays(6)).param("endD", today).param("me", me.employeeId()).query(DayCount.class).list();
    }
}
