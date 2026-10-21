package com.krednova.payroll.config;

import java.time.LocalDate;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Demo accounts for local development only (profile "dev"). Never runs in production. */
@Component
@Profile("dev")
public class DevDataSeeder implements CommandLineRunner {
    static final String DEV_PASSWORD = "KredNova@Dev2026";
    private final JdbcClient jdbc; private final PasswordEncoder encoder;
    public DevDataSeeder(JdbcClient jdbc, PasswordEncoder encoder) { this.jdbc = jdbc; this.encoder = encoder; }

    @Override
    public void run(String... args) {
        long sneha = emp("KN-006", "Sneha Patil", "sneha@krednova.test", "HR", "HR Manager", null, 40000, "ADMIN");
        long ravi = emp("KN-002", "Ravi Kulkarni", "ravi@krednova.test", "Engineering", "Engineering Manager", sneha, 45000, "MANAGER");
        emp("KN-001", "Asha Rao", "asha@krednova.test", "Design", "Product Designer", ravi, 20000, "EMPLOYEE");
        emp("KN-003", "Meera Iyer", "meera@krednova.test", "Engineering", "Software Engineer", ravi, 30000, "EMPLOYEE");
        emp("KN-004", "Imran Shaikh", "imran@krednova.test", "Sales", "Sales Executive", ravi, 22000, "EMPLOYEE");
        emp("KN-005", "Priya Naik", "priya@krednova.test", "Finance", "Accounts Associate", sneha, 25000, "EMPLOYEE");
        jdbc.sql("insert into holidays (holiday_date, name) values (:d, :n)").param("d", LocalDate.of(2026, 11, 1)).param("n", "Karnataka Rajyotsava").update();
        jdbc.sql("insert into holidays (holiday_date, name) values (:d, :n)").param("d", LocalDate.of(2026, 11, 8)).param("n", "Diwali").update();
        System.out.println("Dev accounts ready: sneha@ (admin), ravi@ (manager), asha@ (employee) krednova.test, password " + DEV_PASSWORD);
    }

    private long emp(String code, String name, String email, String dept, String title, Long mgr, int basic, String role) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.sql("insert into employees (employee_code, full_name, email, department, job_title, manager_id, joining_date, status, basic_salary, hra, other_allowances) "
                + "values (:c, :n, :e, :d, :t, :m, :j, 'ACTIVE', :b, :h, :o)")
            .param("c", code).param("n", name).param("e", email).param("d", dept).param("t", title).param("m", mgr, java.sql.Types.BIGINT)
            .param("j", LocalDate.of(2025, 4, 1)).param("b", basic).param("h", basic * 0.4).param("o", basic * 0.1).update(kh, "id");
        long id = kh.getKey().longValue();
        jdbc.sql("insert into users (email, password_hash, role, employee_id) values (:e, :p, :r, :id)")
            .param("e", email).param("p", encoder.encode(DEV_PASSWORD)).param("r", role).param("id", id).update();
        for (var b : new String[][] {{"CASUAL", "8"}, {"SICK", "6"}, {"EARNED", "12"}})
            jdbc.sql("insert into leave_balances (employee_id, leave_type, days_left) values (:id, :t, :d)").param("id", id).param("t", b[0]).param("d", Integer.parseInt(b[1])).update();
        return id;
    }
}
