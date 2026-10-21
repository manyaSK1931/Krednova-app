CREATE TABLE employees (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  employee_code VARCHAR(20) NOT NULL UNIQUE,
  full_name VARCHAR(100) NOT NULL,
  email VARCHAR(190) NOT NULL UNIQUE,
  department VARCHAR(60) NOT NULL,
  job_title VARCHAR(80) NOT NULL,
  manager_id BIGINT NULL,
  joining_date DATE NOT NULL,
  status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
  basic_salary DECIMAL(12,2) NOT NULL,
  hra DECIMAL(12,2) NOT NULL,
  other_allowances DECIMAL(12,2) NOT NULL,
  CONSTRAINT fk_emp_manager FOREIGN KEY (manager_id) REFERENCES employees (id)
);
CREATE TABLE users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  email VARCHAR(190) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  role VARCHAR(10) NOT NULL,
  employee_id BIGINT NOT NULL UNIQUE,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  failed_attempts INT NOT NULL DEFAULT 0,
  locked_until TIMESTAMP NULL,
  CONSTRAINT fk_user_emp FOREIGN KEY (employee_id) REFERENCES employees (id)
);
CREATE TABLE attendance (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  employee_id BIGINT NOT NULL,
  work_date DATE NOT NULL,
  check_in TIMESTAMP NOT NULL,
  check_out TIMESTAMP NULL,
  status VARCHAR(10) NOT NULL,
  worked_minutes INT NULL,
  CONSTRAINT uq_att UNIQUE (employee_id, work_date),
  CONSTRAINT fk_att_emp FOREIGN KEY (employee_id) REFERENCES employees (id)
);
CREATE TABLE leave_balances (
  employee_id BIGINT NOT NULL,
  leave_type VARCHAR(10) NOT NULL,
  days_left INT NOT NULL,
  PRIMARY KEY (employee_id, leave_type),
  CONSTRAINT fk_bal_emp FOREIGN KEY (employee_id) REFERENCES employees (id)
);
CREATE TABLE leave_requests (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  employee_id BIGINT NOT NULL,
  leave_type VARCHAR(10) NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  days INT NOT NULL,
  reason VARCHAR(200) NOT NULL,
  status VARCHAR(10) NOT NULL DEFAULT 'PENDING',
  reviewed_by BIGINT NULL,
  reviewed_at TIMESTAMP NULL,
  CONSTRAINT fk_lr_emp FOREIGN KEY (employee_id) REFERENCES employees (id)
);
CREATE TABLE holidays (
  holiday_date DATE PRIMARY KEY,
  name VARCHAR(100) NOT NULL
);
CREATE TABLE payroll_runs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  period CHAR(7) NOT NULL UNIQUE,
  status VARCHAR(12) NOT NULL,
  created_by BIGINT NOT NULL,
  approved_by BIGINT NULL,
  created_at TIMESTAMP NOT NULL
);
CREATE TABLE payroll_records (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  run_id BIGINT NOT NULL,
  employee_id BIGINT NOT NULL,
  basic_salary DECIMAL(12,2) NOT NULL,
  hra DECIMAL(12,2) NOT NULL,
  other_allowances DECIMAL(12,2) NOT NULL,
  gross_pay DECIMAL(12,2) NOT NULL,
  pf DECIMAL(12,2) NOT NULL,
  lop_days INT NOT NULL,
  lop_amount DECIMAL(12,2) NOT NULL,
  total_deductions DECIMAL(12,2) NOT NULL,
  net_pay DECIMAL(12,2) NOT NULL,
  CONSTRAINT uq_rec UNIQUE (run_id, employee_id),
  CONSTRAINT fk_rec_run FOREIGN KEY (run_id) REFERENCES payroll_runs (id),
  CONSTRAINT fk_rec_emp FOREIGN KEY (employee_id) REFERENCES employees (id)
);
CREATE TABLE audit_logs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  actor_id BIGINT NOT NULL,
  action VARCHAR(60) NOT NULL,
  detail VARCHAR(255) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
