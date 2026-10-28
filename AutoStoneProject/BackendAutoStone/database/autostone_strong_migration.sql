-- AutoStone strong monitoring/flow database hardening.
-- MySQL 8.x. Run after autostone_schema.sql and autostone_flow_upgrade.sql.
-- The statements are written to be safe to re-run on an already upgraded database.
USE autostone;

CREATE TABLE IF NOT EXISTS t_as_view_filter (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  view_id BIGINT NOT NULL,
  filter_field VARCHAR(64) NOT NULL,
  operator VARCHAR(20) NOT NULL DEFAULT 'EQ',
  filter_value VARCHAR(1000),
  display_order INT NOT NULL DEFAULT 0,
  KEY idx_view_filter_view(view_id),
  CONSTRAINT fk_view_filter_view FOREIGN KEY (view_id) REFERENCES t_as_view(id) ON DELETE CASCADE
);

-- Existing installations may already have these constraints. Add only when absent.
SET @sql := IF((SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='t_as_view_job' AND constraint_name='fk_view_job_view')=0,
 'ALTER TABLE t_as_view_job ADD CONSTRAINT fk_view_job_view FOREIGN KEY (view_id) REFERENCES t_as_view(id) ON DELETE CASCADE','SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='t_as_view_job' AND constraint_name='fk_view_job_job')=0,
 'ALTER TABLE t_as_view_job ADD CONSTRAINT fk_view_job_job FOREIGN KEY (job_code) REFERENCES t_as_job_definition(code) ON DELETE CASCADE','SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='t_as_machine_health' AND constraint_name='fk_machine_health_machine')=0,
 'ALTER TABLE t_as_machine_health ADD CONSTRAINT fk_machine_health_machine FOREIGN KEY (machine_id) REFERENCES t_as_machine(id) ON DELETE CASCADE','SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='t_as_job_run_event' AND constraint_name='fk_run_event_run')=0,
 'ALTER TABLE t_as_job_run_event ADD CONSTRAINT fk_run_event_run FOREIGN KEY (job_run_id) REFERENCES t_as_job_run(id) ON DELETE CASCADE','SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='t_as_event_queue_error' AND constraint_name='fk_event_error_event')=0,
 'ALTER TABLE t_as_event_queue_error ADD CONSTRAINT fk_event_error_event FOREIGN KEY (original_event_id) REFERENCES t_as_event_queue(id) ON DELETE SET NULL','SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='t_as_event_queue' AND constraint_name='fk_event_queue_job')=0,
 'ALTER TABLE t_as_event_queue ADD CONSTRAINT fk_event_queue_job FOREIGN KEY (job_code) REFERENCES t_as_job_definition(code) ON DELETE CASCADE','SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='t_as_job_run' AND constraint_name='fk_job_run_job')=0,
 'ALTER TABLE t_as_job_run ADD CONSTRAINT fk_job_run_job FOREIGN KEY (job_code) REFERENCES t_as_job_definition(code) ON DELETE CASCADE','SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='t_as_job_run' AND constraint_name='fk_job_run_machine')=0,
 'ALTER TABLE t_as_job_run ADD CONSTRAINT fk_job_run_machine FOREIGN KEY (machine_id) REFERENCES t_as_machine(id) ON DELETE SET NULL','SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='t_as_job_condition' AND constraint_name='fk_job_condition_job')=0,
 'ALTER TABLE t_as_job_condition ADD CONSTRAINT fk_job_condition_job FOREIGN KEY (job_code) REFERENCES t_as_job_definition(code) ON DELETE CASCADE','SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='t_as_job_condition' AND constraint_name='fk_job_condition_parent')=0,
 'ALTER TABLE t_as_job_condition ADD CONSTRAINT fk_job_condition_parent FOREIGN KEY (depends_on_code) REFERENCES t_as_job_definition(code) ON DELETE CASCADE','SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='t_as_job_definition' AND constraint_name='fk_job_definition_box')=0,
 'ALTER TABLE t_as_job_definition ADD CONSTRAINT fk_job_definition_box FOREIGN KEY (box_code) REFERENCES t_as_job_definition(code) ON DELETE SET NULL','SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='t_as_job_definition' AND constraint_name='fk_job_definition_machine')=0,
 'ALTER TABLE t_as_job_definition ADD CONSTRAINT fk_job_definition_machine FOREIGN KEY (machine_id) REFERENCES t_as_machine(id) ON DELETE SET NULL','SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='t_as_alert' AND constraint_name='fk_alert_run')=0,
 'ALTER TABLE t_as_alert ADD CONSTRAINT fk_alert_run FOREIGN KEY (job_run_id) REFERENCES t_as_job_run(id) ON DELETE SET NULL','SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='t_as_alert' AND constraint_name='fk_alert_group')=0,
 'ALTER TABLE t_as_alert ADD CONSTRAINT fk_alert_group FOREIGN KEY (alert_group_id) REFERENCES t_as_alert_group(id) ON DELETE SET NULL','SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='t_as_view' AND constraint_name='fk_view_owner')=0,
 'ALTER TABLE t_as_view ADD CONSTRAINT fk_view_owner FOREIGN KEY (owner_id) REFERENCES t_as_user(id) ON DELETE SET NULL','SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

ALTER TABLE t_as_job_condition ADD KEY IF NOT EXISTS idx_condition_pair(job_code, depends_on_code, condition_type);
ALTER TABLE t_as_job_run ADD KEY IF NOT EXISTS idx_job_status_time(job_code, status, start_time);
ALTER TABLE t_as_job_run ADD KEY IF NOT EXISTS idx_run_tree(run_id, box_run_id, status, id);
ALTER TABLE t_as_event_queue ADD KEY IF NOT EXISTS idx_event_job_time(job_code, create_time);
ALTER TABLE t_as_view_job ADD KEY IF NOT EXISTS idx_view_order(view_id, display_order, job_code);
