-- Optional one-time migration from the existing AutoStone t_as_* operational schema
-- into the normalized enterprise monitoring schema.
USE autostone;

INSERT INTO box_definition(box_id, box_name, parent_box_id, version, enabled, created_at, updated_at)
SELECT code, job_name, box_code, version, COALESCE(enabled,1), COALESCE(create_time,NOW()), COALESCE(update_time,NOW())
FROM t_as_job_definition
WHERE job_type='BOX'
ON DUPLICATE KEY UPDATE box_name=VALUES(box_name), parent_box_id=VALUES(parent_box_id), version=VALUES(version), enabled=VALUES(enabled), updated_at=VALUES(updated_at);

INSERT INTO job_definition(job_id, job_name, job_type, parent_box_id, version, description, command, host_name, enabled, hold_flag, created_at, updated_at)
SELECT code, job_name, job_type, box_code, version, description, command, host_name, COALESCE(enabled,1), COALESCE(hold,0), COALESCE(create_time,NOW()), COALESCE(update_time,NOW())
FROM t_as_job_definition
ON DUPLICATE KEY UPDATE job_name=VALUES(job_name), job_type=VALUES(job_type), parent_box_id=VALUES(parent_box_id), version=VALUES(version), description=VALUES(description), command=VALUES(command), host_name=VALUES(host_name), enabled=VALUES(enabled), hold_flag=VALUES(hold_flag), updated_at=VALUES(updated_at);

INSERT INTO job_dependency(dependency_id, job_id, depends_on_job_id, dependency_type, condition_expression, exit_code_value, enabled)
SELECT id, job_code, depends_on_code, condition_type, raw_expression, exit_code_value, TRUE
FROM t_as_job_condition
ON DUPLICATE KEY UPDATE dependency_type=VALUES(dependency_type), condition_expression=VALUES(condition_expression), exit_code_value=VALUES(exit_code_value);

INSERT INTO execution_server(server_id, server_name, host_name, status)
SELECT id, machine_name, COALESCE(ip_address,machine_name), CASE WHEN status=1 THEN 'ACTIVE' ELSE 'INACTIVE' END
FROM t_as_machine
ON DUPLICATE KEY UPDATE server_name=VALUES(server_name), host_name=VALUES(host_name), status=VALUES(status);

INSERT INTO execution_history(execution_id, job_id, run_id, attempt, status, server_id, pid, exit_code, started_at, ended_at, message, stdout_path, stderr_path, created_at)
SELECT id, job_code, COALESCE(run_id,UUID()), COALESCE(attempt,1), status, machine_id, pid, exit_code, start_time, end_time, message, std_out_path, std_err_path, COALESCE(start_time,NOW())
FROM t_as_job_run
ON DUPLICATE KEY UPDATE status=VALUES(status), ended_at=VALUES(ended_at), message=VALUES(message), stdout_path=VALUES(stdout_path), stderr_path=VALUES(stderr_path);
