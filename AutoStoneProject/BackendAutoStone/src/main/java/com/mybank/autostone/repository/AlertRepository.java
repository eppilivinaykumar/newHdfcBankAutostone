package com.mybank.autostone.repository;
import com.mybank.autostone.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface AlertRepository extends JpaRepository<Alert,Long> {
    List<Alert> findTop100ByOrderByCreateTimeDesc();
    Optional<Alert> findFirstBySign(String sign);
}
