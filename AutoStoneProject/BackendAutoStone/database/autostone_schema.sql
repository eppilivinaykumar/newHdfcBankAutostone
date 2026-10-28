-- AutoStone enterprise AutoSys-style schema.
-- MySQL 8.x. The *_extension fields are the operational fields needed by the
-- existing AutoStone UI while the core tables follow the supplied design.

CREATE DATABASE IF NOT EXISTS autostone;
USE autostone;

CREATE TABLE IF NOT EXISTS t_as_application (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, app_name VARCHAR(255) NOT NULL, description VARCHAR(512), owner VARCHAR(128),
 create_time DATETIME, update_time DATETIME, UNIQUE KEY uniq_app_name (app_name)
);
CREATE TABLE IF NOT EXISTS t_as_user (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, user_name VARCHAR(128) NOT NULL, email VARCHAR(255), user_type TINYINT DEFAULT 0,
 create_time DATETIME, UNIQUE KEY uniq_user_name (user_name)
);
CREATE TABLE IF NOT EXISTS t_as_machine (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, machine_name VARCHAR(255) NOT NULL, machine_type VARCHAR(50), ip_address VARCHAR(64),
 status TINYINT DEFAULT 1, last_heartbeat DATETIME, UNIQUE KEY uniq_machine_name (machine_name)
);
CREATE TABLE IF NOT EXISTS t_as_calendar (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, calendar_name VARCHAR(255) NOT NULL, description VARCHAR(512), definition TEXT,
 create_time DATETIME, UNIQUE KEY uniq_calendar_name (calendar_name)
);

CREATE TABLE IF NOT EXISTS t_as_job_definition (
 code BIGINT PRIMARY KEY AUTO_INCREMENT,
 version INT NOT NULL DEFAULT 1,
 job_name VARCHAR(255) NOT NULL,
 app_id BIGINT,
 job_type VARCHAR(20) NOT NULL,
 command TEXT,
 box_code BIGINT,
 machine_id BIGINT,
 owner VARCHAR(128), permission VARCHAR(255), application_name VARCHAR(255), group_name VARCHAR(255), run_calendar_name VARCHAR(255), run_calendar_id BIGINT, exclude_calendar_id BIGINT,
 start_times VARCHAR(255), date_conditions TINYINT(1), send_notification TINYINT(1), alarm_if_terminated TINYINT(1), avg_runtime INT, run_window VARCHAR(255), priority INT DEFAULT 0, max_run_alarm_min INT,
 alarm_if_fail TINYINT DEFAULT 1, std_out_file VARCHAR(512), std_err_file VARCHAR(512), n_retrys INT DEFAULT 0,
 profile VARCHAR(255), description VARCHAR(512), release_state TINYINT DEFAULT 1,
 create_time DATETIME, update_time DATETIME,
 -- AutoStone operational/UI fields
 trigger_type VARCHAR(20), cron_expression VARCHAR(255), timezone VARCHAR(100), schedule_start_time DATETIME,
 schedule_end_time DATETIME, misfire_policy VARCHAR(30), condition_expression VARCHAR(2000), host_name VARCHAR(255),
 execution_type VARCHAR(30), enabled TINYINT(1) DEFAULT 1, hold TINYINT(1) DEFAULT 0, auto_hold TINYINT(1) DEFAULT 0, ice TINYINT(1) DEFAULT 0,
 no_execution TINYINT(1) DEFAULT 0, retry_count INT DEFAULT 0, retry_interval_seconds INT DEFAULT 60,
 timeout_seconds INT DEFAULT 0, working_directory VARCHAR(2000), arguments VARCHAR(2000), concurrency_policy VARCHAR(20) DEFAULT 'ALLOW',
 UNIQUE KEY uniq_job_name (job_name), KEY idx_box_code (box_code), KEY idx_machine_id (machine_id), KEY idx_trigger_enabled (trigger_type,enabled), CONSTRAINT fk_job_definition_box FOREIGN KEY (box_code) REFERENCES t_as_job_definition(code) ON DELETE SET NULL, CONSTRAINT fk_job_definition_machine FOREIGN KEY (machine_id) REFERENCES t_as_machine(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS t_as_job_definition_log (
 code BIGINT NOT NULL, version INT NOT NULL, job_name VARCHAR(255) NOT NULL, job_type VARCHAR(20) NOT NULL,
 command TEXT, box_code BIGINT, definition_snapshot TEXT, operator VARCHAR(128), operate_time DATETIME,
 PRIMARY KEY (code, version)
);

CREATE TABLE IF NOT EXISTS t_as_job_condition (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, job_code BIGINT NOT NULL, depends_on_code BIGINT NOT NULL,
 condition_type VARCHAR(20) NOT NULL DEFAULT 'SUCCESS', exit_code_value INT, raw_expression VARCHAR(512),
 KEY idx_job_code (job_code), KEY idx_depends_on (depends_on_code), CONSTRAINT fk_job_condition_job FOREIGN KEY (job_code) REFERENCES t_as_job_definition(code) ON DELETE CASCADE, CONSTRAINT fk_job_condition_parent FOREIGN KEY (depends_on_code) REFERENCES t_as_job_definition(code) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS t_as_event_queue (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, event_type VARCHAR(30) NOT NULL, job_code BIGINT NOT NULL,
 priority INT DEFAULT 0, requested_by VARCHAR(128), payload TEXT, create_time DATETIME,
 KEY idx_priority_id (priority,id), KEY idx_event_job_time(job_code,create_time), CONSTRAINT fk_event_queue_job FOREIGN KEY (job_code) REFERENCES t_as_job_definition(code) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS t_as_event_queue_error (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, original_event_id BIGINT, event_type VARCHAR(30), job_code BIGINT,
 error_message TEXT, fail_time DATETIME
);

CREATE TABLE IF NOT EXISTS t_as_job_run (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, job_code BIGINT NOT NULL, job_version INT NOT NULL DEFAULT 1,
 run_number INT NOT NULL DEFAULT 1, box_run_id BIGINT, machine_id BIGINT, status VARCHAR(20) NOT NULL,
 pid INT, exit_code INT, start_time DATETIME, end_time DATETIME, std_out_path VARCHAR(512), std_err_path VARCHAR(512),
 retry_count INT DEFAULT 0,
 -- workflow_run_id groups one BOX/workflow tree while run_id stays unique per execution
 workflow_run_id CHAR(36),
 -- AutoStone operational/UI fields
 run_id CHAR(36), message VARCHAR(5000), host VARCHAR(255), attempt INT DEFAULT 1, log_path VARCHAR(2000), runtime_hold TINYINT(1) NOT NULL DEFAULT 0, hold_reason VARCHAR(40),
 KEY idx_job_run (job_code,id), KEY idx_start_time (start_time,end_time), KEY idx_box_run (box_run_id), KEY idx_run_uuid_job (run_id,job_code,id), KEY idx_workflow_run (workflow_run_id,job_code,id), KEY idx_job_status_time(job_code,status,start_time), KEY idx_run_tree(run_id,box_run_id,status,id),
 CONSTRAINT fk_job_run_job FOREIGN KEY (job_code) REFERENCES t_as_job_definition(code) ON DELETE CASCADE, CONSTRAINT fk_job_run_machine FOREIGN KEY (machine_id) REFERENCES t_as_machine(id) ON DELETE SET NULL,
 CONSTRAINT fk_job_run_parent_box_run FOREIGN KEY (box_run_id) REFERENCES t_as_job_run(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS t_as_global_variable (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, var_name VARCHAR(255) NOT NULL, var_value TEXT,
 scope VARCHAR(20) DEFAULT 'GLOBAL', scope_ref_id BIGINT, update_time DATETIME,
 UNIQUE KEY uniq_var_scope (var_name,scope,scope_ref_id)
);
CREATE TABLE IF NOT EXISTS t_as_alert (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, title VARCHAR(512), content TEXT, sign CHAR(40), alert_status TINYINT DEFAULT 0,
 alert_reason TINYINT, job_run_id BIGINT, alert_group_id BIGINT, create_time DATETIME, KEY idx_sign (sign), KEY idx_alert_run(job_run_id), CONSTRAINT fk_alert_run FOREIGN KEY (job_run_id) REFERENCES t_as_job_run(id) ON DELETE SET NULL
);
CREATE TABLE IF NOT EXISTS t_as_alert_group (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, group_name VARCHAR(255) NOT NULL, notify_targets VARCHAR(512),
 description VARCHAR(255), UNIQUE KEY uniq_group_name (group_name)
);
CREATE TABLE IF NOT EXISTS t_as_alert_policy (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, enabled TINYINT(1) DEFAULT 1, alert_on_failure TINYINT(1) DEFAULT 1,
 alert_on_timeout TINYINT(1) DEFAULT 1, alert_on_server_unavailable TINYINT(1) DEFAULT 1,
 max_run_minutes INT DEFAULT 30, updated_at DATETIME
);

INSERT INTO t_as_alert_policy(enabled,alert_on_failure,alert_on_timeout,alert_on_server_unavailable,max_run_minutes,updated_at)
SELECT 1,1,1,1,30,NOW() WHERE NOT EXISTS (SELECT 1 FROM t_as_alert_policy);


-- Durable execution state transition history used by real-time monitoring.
CREATE TABLE IF NOT EXISTS t_as_job_run_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_run_id BIGINT NOT NULL,
  event_type VARCHAR(50) NOT NULL,
  old_status VARCHAR(30),
  new_status VARCHAR(30),
  message VARCHAR(2000),
  actor VARCHAR(128),
  created_at DATETIME NOT NULL,
  KEY idx_run_event_created (created_at,id),
  KEY idx_run_event_run (job_run_id,created_at),
  CONSTRAINT fk_run_event_run FOREIGN KEY (job_run_id) REFERENCES t_as_job_run(id)
);
