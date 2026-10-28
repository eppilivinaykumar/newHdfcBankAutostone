package com.mybank.autostone.repository;

import com.mybank.autostone.entity.JobRunEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface JobRunEventRepository extends JpaRepository<JobRunEvent, Long> {
    List<JobRunEvent> findByIdGreaterThanOrderByIdAsc(Long id);
}
