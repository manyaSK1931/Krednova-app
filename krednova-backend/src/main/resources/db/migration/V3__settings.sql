CREATE TABLE app_settings (
  setting_key VARCHAR(40) PRIMARY KEY,
  setting_value VARCHAR(100) NOT NULL
);
INSERT INTO app_settings (setting_key, setting_value) VALUES ('shift_start', '09:00');
INSERT INTO app_settings (setting_key, setting_value) VALUES ('shift_end', '18:00');
INSERT INTO app_settings (setting_key, setting_value) VALUES ('grace_minutes', '10');
INSERT INTO app_settings (setting_key, setting_value) VALUES ('casual_days', '8');
INSERT INTO app_settings (setting_key, setting_value) VALUES ('sick_days', '6');
INSERT INTO app_settings (setting_key, setting_value) VALUES ('earned_days', '12');
