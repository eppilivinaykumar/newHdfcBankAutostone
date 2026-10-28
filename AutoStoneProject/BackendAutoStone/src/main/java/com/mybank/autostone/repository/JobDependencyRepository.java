package com.mybank.autostone.repository;

import com.mybank.autostone.entity.JobDependency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface JobDependencyRepository extends JpaRepository<JobDependency, Long> {
    @Query("select d from JobDependency d where d.job.id = :jobId")
    List<JobDependency> findByJobId(@Param("jobId") Long jobId);
    @Query("select d from JobDependency d where d.dependsOnJob.id = :jobId")
    List<JobDependency> findByDependsOnJobId(@Param("jobId") Long jobId);
    @Query("select d from JobDependency d where d.job.id in :jobIds")
    List<JobDependency> findByJobIdIn(@Param("jobIds") List<Long> jobIds);
}
