package com.mybank.autostone.repository;
import com.mybank.autostone.entity.JobDefinitionLog;
import com.mybank.autostone.entity.JobDefinitionLogId;
import org.springframework.data.jpa.repository.JpaRepository;
public interface JobDefinitionLogRepository extends JpaRepository<JobDefinitionLog, JobDefinitionLogId> {}
