-- AutoStone Flow / monitoring upgrade.
-- Run after autostone_schema.sql. MySQL 8.x.
USE autostone;

CREATE TABLE IF NOT EXISTS t_as_view (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  view_name VARCHAR(255) NOT NULL,
  description VARCHAR(1000),
  owner_id BIGINT,
  enabled TINYINT(1) DEFAULT 1,
  auto_refresh_seconds INT DEFAULT 5,
  created_at DATETIME,
  updated_at DATETIME,
  UNIQUE KEY uq_view_name(view_name)
);

CREATE TABLE IF NOT EXISTS t_as_view_job (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  view_id BIGINT NOT NULL,
  job_code BIGINT NOT NULL,
  display_order INT DEFAULT 0,
  UNIQUE KEY uq_view_job(view_id, job_code),
  KEY idx_view_job_view(view_id),
  KEY idx_view_job_job(job_code)
);

CREATE TABLE IF NOT EXISTS t_as_machine_health (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  machine_id BIGINT NOT NULL,
  status VARCHAR(30) NOT NULL,
  response_time_ms INT,
  detail VARCHAR(1000),
  checked_at DATETIME NOT NULL,
  KEY idx_machine_health(machine_id, checked_at)
);

CREATE TABLE IF NOT EXISTS t_as_job_run_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_run_id BIGINT NOT NULL,
  event_type VARCHAR(50) NOT NULL,
  old_status VARCHAR(30),
  new_status VARCHAR(30),
  message VARCHAR(2000),
  actor VARCHAR(128),
  created_at DATETIME NOT NULL,
  KEY idx_run_event(job_run_id, created_at)
);

-- Durable command/event lifecycle fields. Safe for new installations.
ALTER TABLE t_as_event_queue ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'NEW';
ALTER TABLE t_as_event_queue ADD COLUMN IF NOT EXISTS retry_count INT DEFAULT 0;
ALTER TABLE t_as_event_queue ADD COLUMN IF NOT EXISTS worker_id VARCHAR(128);
ALTER TABLE t_as_event_queue ADD COLUMN IF NOT EXISTS processed_at DATETIME;
ALTER TABLE t_as_event_queue ADD COLUMN IF NOT EXISTS error_message TEXT;
ALTER TABLE t_as_event_queue ADD KEY IF NOT EXISTS idx_event_status_priority(status, priority, id);
