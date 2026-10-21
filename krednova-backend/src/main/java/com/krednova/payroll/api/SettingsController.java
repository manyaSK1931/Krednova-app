package com.krednova.payroll.api;

import com.krednova.payroll.config.SettingsService;
import com.krednova.payroll.config.SettingsService.Cfg;
import com.krednova.payroll.security.AppUserDetailsService.P;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Company settings (admin changes, everyone reads) and the holiday list. */
@RestController
@RequestMapping("/api/v1")
public class SettingsController {
    public record CfgReq(@NotNull @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d") String shiftStart,
                         @NotNull @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d") String shiftEnd,
                         @NotNull @Min(0) @Max(120) Integer graceMinutes,
                         @NotNull @Min(0) @Max(60) Integer casualDays,
                         @NotNull @Min(0) @Max(60) Integer sickDays,
                         @NotNull @Min(0) @Max(60) Integer earnedDays) {}
    public record Hol(LocalDate holidayDate, String name) {}
    public record NewHol(@NotNull LocalDate date, @NotBlank @Size(max = 100) String name) {}

    private final SettingsService settings; private final JdbcClient jdbc; private final Audit audit; private final ZoneId zone;

    public SettingsController(SettingsService settings, JdbcClient jdbc, Audit audit, @Value("${krednova.timezone}") String tz) {
        this.settings = settings; this.jdbc = jdbc; this.audit = audit; this.zone = ZoneId.of(tz);
    }

    @GetMapping("/settings")
    Cfg get() { return settings.load(); }

    @PutMapping("/settings")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    Cfg save(@Valid @RequestBody CfgReq r, @AuthenticationPrincipal P me) {
        if (!LocalTime.parse(r.shiftEnd()).isAfter(LocalTime.parse(r.shiftStart())))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Shift end must be after shift start.");
        Cfg c = new Cfg(r.shiftStart(), r.shiftEnd(), r.graceMinutes(), r.casualDays(), r.sickDays(), r.earnedDays());
        settings.save(c);
        audit.log(me.employeeId(), "SETTINGS_CHANGED", "work hours and leave policy updated");
        return c;
    }

    @GetMapping("/holidays")
    List<Hol> holidays() {
        return jdbc.sql("select holiday_date, name from holidays where holiday_date >= :d order by holiday_date limit 20")
            .param("d", LocalDate.now(zone)).query(Hol.class).list();
    }

    @PostMapping("/holidays")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    ResponseEntity<Void> addHoliday(@Valid @RequestBody NewHol r, @AuthenticationPrincipal P me) {
        jdbc.sql("insert into holidays (holiday_date, name) values (:d, :n)").param("d", r.date()).param("n", r.name().trim()).update(); // same date twice returns 409
        audit.log(me.employeeId(), "HOLIDAY_ADDED", r.date().toString());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/holidays/{date}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    ResponseEntity<Void> removeHoliday(@PathVariable String date, @AuthenticationPrincipal P me) {
        LocalDate d;
        try { d = LocalDate.parse(date); } catch (DateTimeParseException e) { throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid date."); }
        int n = jdbc.sql("delete from holidays where holiday_date = :d").param("d", d).update();
        if (n == 0) throw new ApiException(HttpStatus.NOT_FOUND, "Holiday not found.");
        audit.log(me.employeeId(), "HOLIDAY_REMOVED", d.toString());
        return ResponseEntity.noContent().build();
    }
}
