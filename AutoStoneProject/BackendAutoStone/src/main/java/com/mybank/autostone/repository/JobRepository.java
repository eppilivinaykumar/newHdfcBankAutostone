package com.mybank.autostone.repository;

import com.mybank.autostone.entity.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface JobRepository extends JpaRepository<Job, Long> {
    @Query("select j from Job j where j.box.id = :boxId")
    List<Job> findByBoxId(@Param("boxId") Long boxId);
    Optional<Job> findByName(String name);
    Page<Job> findByNameContainingIgnoreCase(String name, Pageable pageable);
    Page<Job> findByBoxId(Long boxId, Pageable pageable);
    Page<Job> findByBoxIsNull(Pageable pageable);
    Page<Job> findByBoxIsNullAndNameContainingIgnoreCase(String name, Pageable pageable);
    List<Job> findByUpdatedAtAfter(LocalDateTime since);
    Page<Job> findByBoxIdAndNameContainingIgnoreCase(Long boxId, String name, Pageable pageable);
    boolean existsByName(String name);

    @Query("select j from Job j where j.box.id = :boxId and j.id not in (select d.job.id from JobDependency d)")
    List<Job> findRootJobsOfBox(@Param("boxId") Long boxId);
}
