package com.mybank.autostone.repository;
import com.mybank.autostone.entity.EventQueue;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EventQueueRepository extends JpaRepository<EventQueue,Long> {}
