package com.krednova.payroll.api;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Records who did what. Never put salary figures or passwords in the detail text. */
@Component
public class Audit {
    private final JdbcClient jdbc;
    public Audit(JdbcClient jdbc) { this.jdbc = jdbc; }
    public void log(long actorEmployeeId, String action, String detail) {
        jdbc.sql("insert into audit_logs (actor_id, action, detail) values (:a, :x, :d)")
            .param("a", actorEmployeeId).param("x", action).param("d", detail).update();
    }
}
