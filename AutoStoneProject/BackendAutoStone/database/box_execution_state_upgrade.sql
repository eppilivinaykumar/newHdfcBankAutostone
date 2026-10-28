-- AutoStone BOX/JOB execution-state upgrade. Run after autostone_schema.sql.
-- The existing legacy-compatible model is retained: box_code stores the parent BOX
-- for both JOB and nested BOX definitions, while box_run_id links an execution to
-- the concrete parent BOX execution for that workflow run.

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
  CONSTRAINT fk_run_event_run FOREIGN KEY (job_run_id) REFERENCES t_as_job_run(id) ON DELETE CASCADE
);

ALTER TABLE t_as_job_definition ADD KEY IF NOT EXISTS idx_as_job_parent_type (box_code, job_type);
ALTER TABLE t_as_job_run ADD KEY IF NOT EXISTS idx_as_run_box_status (box_run_id, status, id);
ALTER TABLE t_as_job_run ADD KEY IF NOT EXISTS idx_as_run_uuid_job (run_id, job_code, id);

-- IMPORTANT: run_id identifies a workflow instance, so all jobs/BOXes in that
-- instance intentionally share it. It must NOT be globally unique. Remove the
-- earlier incorrect unique index if that migration was applied.
SET @drop_run_uuid_sql := (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE t_as_job_run DROP INDEX uq_job_run_uuid',
    'SELECT 1')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 't_as_job_run' AND index_name = 'uq_job_run_uuid');
PREPARE drop_run_uuid_stmt FROM @drop_run_uuid_sql;
EXECUTE drop_run_uuid_stmt;
DEALLOCATE PREPARE drop_run_uuid_stmt;

SET @parent_fk_exists := (SELECT COUNT(*) FROM information_schema.table_constraints
  WHERE constraint_schema = DATABASE() AND table_name = 't_as_job_run'
    AND constraint_name = 'fk_job_run_parent_box_run');
SET @parent_fk_sql := IF(@parent_fk_exists = 0,
  'ALTER TABLE t_as_job_run ADD CONSTRAINT fk_job_run_parent_box_run FOREIGN KEY (box_run_id) REFERENCES t_as_job_run(id) ON DELETE SET NULL',
  'SELECT 1');
PREPARE parent_fk_stmt FROM @parent_fk_sql;
EXECUTE parent_fk_stmt;
DEALLOCATE PREPARE parent_fk_stmt;

ALTER TABLE t_as_job_run ADD COLUMN IF NOT EXISTS runtime_hold TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE t_as_job_run ADD COLUMN IF NOT EXISTS hold_reason VARCHAR(40);

ALTER TABLE t_as_job_run ADD KEY IF NOT EXISTS idx_run_tree(run_id,box_run_id,status,id);


-- Execution correlation fix: each t_as_job_run gets a unique run_id; workflow_run_id groups one workflow tree.
ALTER TABLE t_as_job_run ADD COLUMN IF NOT EXISTS workflow_run_id CHAR(36);
ALTER TABLE t_as_job_run ADD KEY IF NOT EXISTS idx_workflow_run(workflow_run_id,job_code,id);
UPDATE t_as_job_run SET workflow_run_id = run_id WHERE workflow_run_id IS NULL;
