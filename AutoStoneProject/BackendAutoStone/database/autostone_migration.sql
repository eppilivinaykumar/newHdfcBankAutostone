-- Run this only when the core t_as_* schema already exists from the supplied SQL.
USE autostone;
ALTER TABLE t_as_job_definition MODIFY code BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE t_as_job_definition ADD COLUMN IF NOT EXISTS trigger_type VARCHAR(20);
ALTER TABLE t_as_job_definition ADD COLUMN IF NOT EXISTS cron_expression VARCHAR(255);
ALTER TABLE t_as_job_definition ADD COLUMN IF NOT EXISTS timezone VARCHAR(100);
ALTER TABLE t_as_job_definition ADD COLUMN IF NOT EXISTS schedule_start_time DATETIME;
ALTER TABLE t_as_job_definition ADD COLUMN IF NOT EXISTS schedule_end_time DATETIME;
ALTER TABLE t_as_job_definition ADD COLUMN IF NOT EXISTS misfire_policy VARCHAR(30);
ALTER TABLE t_as_job_definition ADD COLUMN IF NOT EXISTS condition_expression VARCHAR(2000);
ALTER TABLE t_as_job_definition ADD COLUMN IF NOT EXISTS host_name VARCHAR(255);
ALTER TABLE t_as_job_definition ADD COLUMN IF NOT EXISTS execution_type VARCHAR(30);
ALTER TABLE t_as_job_definition ADD COLUMN IF NOT EXISTS enabled TINYINT(1) DEFAULT 1;
ALTER TABLE t_as_job_definition ADD COLUMN IF NOT EXISTS hold TINYINT(1) DEFAULT 0;
ALTER TABLE t_as_job_definition ADD COLUMN IF NOT EXISTS ice TINYINT(1) DEFAULT 0;
ALTER TABLE t_as_job_definition ADD COLUMN IF NOT EXISTS no_execution TINYINT(1) DEFAULT 0;
ALTER TABLE t_as_job_definition ADD COLUMN IF NOT EXISTS retry_count INT DEFAULT 0;
ALTER TABLE t_as_job_definition ADD COLUMN IF NOT EXISTS retry_interval_seconds INT DEFAULT 60;
ALTER TABLE t_as_job_definition ADD COLUMN IF NOT EXISTS timeout_seconds INT DEFAULT 0;
ALTER TABLE t_as_job_definition ADD COLUMN IF NOT EXISTS working_directory VARCHAR(2000);
ALTER TABLE t_as_job_definition ADD COLUMN IF NOT EXISTS arguments VARCHAR(2000);
ALTER TABLE t_as_job_definition ADD COLUMN IF NOT EXISTS concurrency_policy VARCHAR(20) DEFAULT 'ALLOW';
ALTER TABLE t_as_job_definition ADD COLUMN IF NOT EXISTS auto_hold TINYINT(1) DEFAULT 0;

ALTER TABLE t_as_job_run ADD COLUMN IF NOT EXISTS run_id CHAR(36);
ALTER TABLE t_as_job_run ADD COLUMN IF NOT EXISTS message VARCHAR(5000);
ALTER TABLE t_as_job_run ADD COLUMN IF NOT EXISTS host VARCHAR(255);
ALTER TABLE t_as_job_run ADD COLUMN IF NOT EXISTS attempt INT DEFAULT 1;
ALTER TABLE t_as_job_run ADD COLUMN IF NOT EXISTS log_path VARCHAR(2000);
CREATE TABLE IF NOT EXISTS t_as_alert_policy (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, enabled TINYINT(1) DEFAULT 1, alert_on_failure TINYINT(1) DEFAULT 1,
 alert_on_timeout TINYINT(1) DEFAULT 1, alert_on_server_unavailable TINYINT(1) DEFAULT 1,
 max_run_minutes INT DEFAULT 30, updated_at DATETIME
);
INSERT INTO t_as_alert_policy(enabled,alert_on_failure,alert_on_timeout,alert_on_server_unavailable,max_run_minutes,updated_at)
SELECT 1,1,1,1,30,NOW() WHERE NOT EXISTS (SELECT 1 FROM t_as_alert_policy);

-- AutoSys JIL metadata retained by the importer.
ALTER TABLE t_as_job_definition ADD COLUMN IF NOT EXISTS application_name VARCHAR(255);
ALTER TABLE t_as_job_definition ADD COLUMN IF NOT EXISTS group_name VARCHAR(255);
ALTER TABLE t_as_job_definition ADD COLUMN IF NOT EXISTS run_calendar_name VARCHAR(255);
ALTER TABLE t_as_job_definition ADD COLUMN IF NOT EXISTS date_conditions TINYINT(1);
ALTER TABLE t_as_job_definition ADD COLUMN IF NOT EXISTS send_notification TINYINT(1);
ALTER TABLE t_as_job_definition ADD COLUMN IF NOT EXISTS alarm_if_terminated TINYINT(1);
ALTER TABLE t_as_job_definition ADD COLUMN IF NOT EXISTS avg_runtime INT;

-- State transition event stream for real-time monitoring (idempotent for new installs).
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

ALTER TABLE t_as_job_run ADD COLUMN IF NOT EXISTS runtime_hold TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE t_as_job_run ADD COLUMN IF NOT EXISTS hold_reason VARCHAR(40);

ALTER TABLE t_as_job_run ADD KEY IF NOT EXISTS idx_run_tree(run_id,box_run_id,status,id);


-- Keep run_id unique per execution while workflow_run_id groups a BOX/workflow tree.
ALTER TABLE t_as_job_run ADD COLUMN IF NOT EXISTS workflow_run_id CHAR(36);
ALTER TABLE t_as_job_run ADD KEY IF NOT EXISTS idx_workflow_run(workflow_run_id,job_code,id);
UPDATE t_as_job_run SET workflow_run_id = run_id WHERE workflow_run_id IS NULL;
