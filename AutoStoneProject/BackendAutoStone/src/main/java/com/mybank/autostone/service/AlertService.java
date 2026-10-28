package com.mybank.autostone.service;

import com.mybank.autostone.entity.Alert;
import com.mybank.autostone.entity.AlertPolicy;
import com.mybank.autostone.entity.JobExecution;
import com.mybank.autostone.entity.JobStatus;
import com.mybank.autostone.repository.AlertPolicyRepository;
import com.mybank.autostone.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AlertService {
    private final AlertRepository alertRepository;
    private final AlertPolicyRepository policyRepository;

    public void onExecutionTerminal(JobExecution execution) {
        if (execution == null) return;
        AlertPolicy p = policyRepository.findAll().stream().findFirst().orElse(null);
        if (p == null) p = policyRepository.save(AlertPolicy.builder().updatedAt(LocalDateTime.now()).build());
        if (!Boolean.TRUE.equals(p.getEnabled())) return;
        JobStatus s = execution.getStatus();
        boolean should = (s == JobStatus.FAILED && Boolean.TRUE.equals(p.getAlertOnFailure()))
                || (s == JobStatus.TIMEOUT && Boolean.TRUE.equals(p.getAlertOnTimeout()));
        if (!should) return;
        int reason = s == JobStatus.TIMEOUT ? 2 : 1;
        String title = execution.getJob().getName() + " " + s.name();
        String content = execution.getMessage() == null ? title : execution.getMessage();
        String sign = sha256(execution.getId()+":"+s+":"+execution.getEndTime());
        if (alertRepository.findFirstBySign(sign).isPresent()) return;
        alertRepository.save(Alert.builder().title(title).content(content).sign(sign).alertReason(reason)
                .jobRunId(execution.getId()).alertStatus(0).createTime(LocalDateTime.now()).build());
    }

    public void onServerUnavailable(String host, String detail) {
        AlertPolicy p = policyRepository.findAll().stream().findFirst().orElse(null);
        if (p == null || !Boolean.TRUE.equals(p.getEnabled()) || !Boolean.TRUE.equals(p.getAlertOnServerUnavailable())) return;
        String sign = sha256("SERVER:"+host+":"+LocalDateTime.now().toLocalDate());
        if (alertRepository.findFirstBySign(sign).isPresent()) return;
        alertRepository.save(Alert.builder().title("Execution server unavailable: "+host)
                .content(detail).sign(sign).alertReason(3).alertStatus(0).createTime(LocalDateTime.now()).build());
    }

    private String sha256(String value) {
        try { byte[] d=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); StringBuilder b=new StringBuilder(); for(byte x:d)b.append(String.format("%02x",x)); return b.substring(0,40); }
        catch(Exception e){ return Integer.toHexString(value.hashCode()); }
    }
}
