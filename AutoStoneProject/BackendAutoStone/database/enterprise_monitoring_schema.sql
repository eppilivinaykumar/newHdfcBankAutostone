-- AutoStone enterprise monitoring model (MySQL 8+)
-- This is the normalized operational contract requested for Monitoring.
-- Existing t_as_* tables remain supported; these tables provide a clean enterprise
-- schema for new deployments/migration and are intentionally compatible with them.
CREATE DATABASE IF NOT EXISTS autostone;
USE autostone;

CREATE TABLE IF NOT EXISTS box_definition (
  box_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  box_name VARCHAR(255) NOT NULL,
  parent_box_id BIGINT NULL,
  version INT NOT NULL DEFAULT 1,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_box_name(box_name),
  KEY idx_box_parent(parent_box_id),
  CONSTRAINT fk_box_parent FOREIGN KEY(parent_box_id) REFERENCES box_definition(box_id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS job_definition (
  job_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_name VARCHAR(255) NOT NULL,
  job_type VARCHAR(20) NOT NULL,
  parent_box_id BIGINT NULL,
  version INT NOT NULL DEFAULT 1,
  description VARCHAR(2000),
  command TEXT,
  host_name VARCHAR(255),
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  hold_flag BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_job_definition_name(job_name),
  KEY idx_job_parent_type(parent_box_id, job_type),
  KEY idx_job_enabled(enabled, job_type),
  CONSTRAINT fk_job_definition_parent_box FOREIGN KEY(parent_box_id) REFERENCES box_definition(box_id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS job_dependency (
  dependency_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_id BIGINT NOT NULL,
  depends_on_job_id BIGINT NOT NULL,
  dependency_type VARCHAR(30) NOT NULL DEFAULT 'SUCCESS',
  condition_expression VARCHAR(2000),
  exit_code_value INT NULL,
  time_expression VARCHAR(255),
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  UNIQUE KEY uq_job_dependency(job_id, depends_on_job_id, dependency_type),
  KEY idx_dependency_parent(depends_on_job_id),
  CONSTRAINT fk_dependency_job FOREIGN KEY(job_id) REFERENCES job_definition(job_id) ON DELETE CASCADE,
  CONSTRAINT fk_dependency_parent FOREIGN KEY(depends_on_job_id) REFERENCES job_definition(job_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS execution_server (
  server_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  server_name VARCHAR(255) NOT NULL,
  host_name VARCHAR(255) NOT NULL,
  port INT DEFAULT 22,
  status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
  last_heartbeat DATETIME NULL,
  UNIQUE KEY uq_execution_server_name(server_name),
  KEY idx_server_status(status, last_heartbeat)
);

CREATE TABLE IF NOT EXISTS agent (
  agent_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  server_id BIGINT NOT NULL,
  agent_name VARCHAR(255) NOT NULL,
  version VARCHAR(64),
  status VARCHAR(30) NOT NULL DEFAULT 'ONLINE',
  last_heartbeat DATETIME,
  UNIQUE KEY uq_agent_name(agent_name),
  KEY idx_agent_server_status(server_id,status),
  CONSTRAINT fk_agent_server FOREIGN KEY(server_id) REFERENCES execution_server(server_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS job_schedule (
  schedule_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_id BIGINT NOT NULL,
  schedule_type VARCHAR(30) NOT NULL,
  cron_expression VARCHAR(255),
  timezone VARCHAR(100) NOT NULL DEFAULT 'UTC',
  start_at DATETIME,
  end_at DATETIME,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  next_fire_at DATETIME,
  KEY idx_schedule_next(enabled,next_fire_at),
  KEY idx_schedule_job(job_id),
  CONSTRAINT fk_schedule_job FOREIGN KEY(job_id) REFERENCES job_definition(job_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS job_status (
  job_id BIGINT PRIMARY KEY,
  status VARCHAR(30) NOT NULL,
  status_at DATETIME NOT NULL,
  run_id CHAR(36),
  message VARCHAR(2000),
  version BIGINT NOT NULL DEFAULT 0,
  KEY idx_status(status,status_at),
  CONSTRAINT fk_job_status_job FOREIGN KEY(job_id) REFERENCES job_definition(job_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS execution_history (
  execution_id BIGINT NOT NULL AUTO_INCREMENT,
  job_id BIGINT NOT NULL,
  run_id CHAR(36) NOT NULL,
  attempt INT NOT NULL DEFAULT 1,
  status VARCHAR(30) NOT NULL,
  server_id BIGINT NULL,
  pid BIGINT NULL,
  exit_code INT NULL,
  started_at DATETIME NULL,
  ended_at DATETIME NULL,
  message VARCHAR(5000),
  stdout_path VARCHAR(2000),
  stderr_path VARCHAR(2000),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY(execution_id),
  KEY idx_execution_job_time(job_id, started_at),
  KEY idx_execution_status_time(status, started_at),
  KEY idx_execution_run(run_id),
  CONSTRAINT fk_execution_job FOREIGN KEY(job_id) REFERENCES job_definition(job_id) ON DELETE CASCADE,
  CONSTRAINT fk_execution_server FOREIGN KEY(server_id) REFERENCES execution_server(server_id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- Partitioning strategy for very large execution volumes: keep the OLTP table FK-safe and
-- move closed historical rows into yearly archive partitions. MySQL InnoDB does not allow
-- foreign keys on partitioned tables, so the archive is intentionally independent.
CREATE TABLE IF NOT EXISTS execution_history_archive (
  execution_id BIGINT NOT NULL, job_id BIGINT NOT NULL, run_id CHAR(36) NOT NULL, attempt INT NOT NULL,
  status VARCHAR(30) NOT NULL, server_id BIGINT NULL, pid BIGINT NULL, exit_code INT NULL,
  started_at DATETIME NOT NULL, ended_at DATETIME NULL, message VARCHAR(5000), stdout_path VARCHAR(2000),
  stderr_path VARCHAR(2000), created_at DATETIME NOT NULL,
  PRIMARY KEY(execution_id, started_at), KEY idx_archive_job_time(job_id, started_at), KEY idx_archive_run(run_id)
) ENGINE=InnoDB
PARTITION BY RANGE COLUMNS(started_at) (
  PARTITION p_before_2026 VALUES LESS THAN ('2026-01-01'),
  PARTITION p_2026 VALUES LESS THAN ('2027-01-01'),
  PARTITION p_2027 VALUES LESS THAN ('2028-01-01'),
  PARTITION p_future VALUES LESS THAN (MAXVALUE)
);

CREATE TABLE IF NOT EXISTS execution_log (
  log_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  execution_id BIGINT NOT NULL,
  stream_type VARCHAR(20) NOT NULL,
  sequence_no BIGINT NOT NULL,
  log_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  payload MEDIUMTEXT NOT NULL,
  KEY idx_execution_log(execution_id, sequence_no)
);

CREATE TABLE IF NOT EXISTS job_action_history (
  action_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_id BIGINT NOT NULL,
  execution_id BIGINT NULL,
  action_type VARCHAR(50) NOT NULL,
  actor VARCHAR(255) NOT NULL,
  old_status VARCHAR(30),
  new_status VARCHAR(30),
  request_payload JSON,
  result_message VARCHAR(2000),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_action_job_time(job_id, created_at),
  KEY idx_action_type_time(action_type, created_at),
  CONSTRAINT fk_action_job FOREIGN KEY(job_id) REFERENCES job_definition(job_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS user_permission (
  permission_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_name VARCHAR(255) NOT NULL,
  resource_type VARCHAR(40) NOT NULL,
  resource_id BIGINT NULL,
  permission_name VARCHAR(80) NOT NULL,
  granted BOOLEAN NOT NULL DEFAULT TRUE,
  UNIQUE KEY uq_user_permission(user_name,resource_type,resource_id,permission_name),
  KEY idx_user_permission(user_name,resource_type)
);

CREATE TABLE IF NOT EXISTS audit_log (
  audit_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  actor VARCHAR(255) NOT NULL,
  action_type VARCHAR(80) NOT NULL,
  resource_type VARCHAR(80) NOT NULL,
  resource_id BIGINT NULL,
  request_id VARCHAR(100),
  ip_address VARCHAR(64),
  old_value JSON,
  new_value JSON,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_audit_resource(resource_type,resource_id,created_at),
  KEY idx_audit_actor(actor,created_at)
);

CREATE TABLE IF NOT EXISTS notification (
  notification_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_name VARCHAR(255),
  channel VARCHAR(30) NOT NULL,
  event_type VARCHAR(50) NOT NULL,
  job_id BIGINT NULL,
  execution_id BIGINT NULL,
  destination VARCHAR(1000),
  payload JSON,
  status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  scheduled_at DATETIME,
  sent_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_notification_status(status,scheduled_at),
  CONSTRAINT fk_notification_job FOREIGN KEY(job_id) REFERENCES job_definition(job_id) ON DELETE SET NULL
);

-- Migration/index hardening for the existing AutoStone tables.
ALTER TABLE t_as_job_definition ADD KEY IF NOT EXISTS idx_as_job_parent_type(box_code,job_type);
ALTER TABLE t_as_job_run ADD KEY IF NOT EXISTS idx_as_run_status_time(status,start_time,end_time);
ALTER TABLE t_as_job_condition ADD KEY IF NOT EXISTS idx_as_condition_job_parent(job_code,depends_on_code,condition_type);
