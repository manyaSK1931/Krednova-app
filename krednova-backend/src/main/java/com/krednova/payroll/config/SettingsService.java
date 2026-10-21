package com.krednova.payroll.config;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Company settings kept in the database (work hours and yearly leave days for new employees). */
@Service
public class SettingsService {
    public record Cfg(String shiftStart, String shiftEnd, int graceMinutes, int casualDays, int sickDays, int earnedDays) {}

    private final JdbcClient jdbc;
    public SettingsService(JdbcClient jdbc) { this.jdbc = jdbc; }

    private String get(String key, String fallback) {
        return jdbc.sql("select setting_value from app_settings where setting_key = :k").param("k", key).query(String.class).optional().orElse(fallback);
    }

    private int getInt(String key, int fallback) {
        try { return Integer.parseInt(get(key, String.valueOf(fallback))); } catch (NumberFormatException e) { return fallback; }
    }

    public Cfg load() {
        return new Cfg(get("shift_start", "09:00"), get("shift_end", "18:00"), getInt("grace_minutes", 10),
            getInt("casual_days", 8), getInt("sick_days", 6), getInt("earned_days", 12));
    }

    public void save(Cfg c) {
        put("shift_start", c.shiftStart()); put("shift_end", c.shiftEnd()); put("grace_minutes", String.valueOf(c.graceMinutes()));
        put("casual_days", String.valueOf(c.casualDays())); put("sick_days", String.valueOf(c.sickDays())); put("earned_days", String.valueOf(c.earnedDays()));
    }

    private void put(String key, String value) {
        int n = jdbc.sql("update app_settings set setting_value = :v where setting_key = :k").param("v", value).param("k", key).update();
        if (n == 0) jdbc.sql("insert into app_settings (setting_key, setting_value) values (:k, :v)").param("k", key).param("v", value).update();
    }
}
