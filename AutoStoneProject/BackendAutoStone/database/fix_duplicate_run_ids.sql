-- One-time repair for existing data created by the old shared-run_id model.
-- It preserves workflow_run_id, which is now the workflow/BOX correlation key.
-- Run this once after deploying the backend change.

ALTER TABLE t_as_job_run ADD COLUMN IF NOT EXISTS workflow_run_id CHAR(36);
UPDATE t_as_job_run SET workflow_run_id = run_id WHERE workflow_run_id IS NULL;

-- Give every old row a run_id if it did not have one.
UPDATE t_as_job_run SET run_id = UUID() WHERE run_id IS NULL;

-- Existing duplicate run_ids are split into unique execution ids.
-- workflow_run_id remains unchanged, so BOX/workflow grouping is not lost.
UPDATE t_as_job_run r
JOIN (
    SELECT run_id
    FROM t_as_job_run
    GROUP BY run_id
    HAVING COUNT(*) > 1
) d ON d.run_id = r.run_id
SET r.run_id = UUID();

ALTER TABLE t_as_job_run ADD KEY IF NOT EXISTS idx_workflow_run(workflow_run_id,job_code,id);

-- Optional hard DB guarantee for future rows. Uncomment after confirming the duplicate repair.
-- ALTER TABLE t_as_job_run ADD UNIQUE KEY uq_job_run_run_id (run_id);
