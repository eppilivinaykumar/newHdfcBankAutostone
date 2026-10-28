package com.mybank.autostone.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {
    private final JdbcTemplate jdbc;
    @Value("${spring.application.name:autostone}") private String app;
    public HealthController(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @GetMapping
    public Map<String,Object> health(){
        Map<String,Object> out=new LinkedHashMap<>(); out.put("application",app); out.put("timestamp",LocalDateTime.now());
        try { Integer one=jdbc.queryForObject("select 1",Integer.class); out.put("database","UP"); out.put("databaseCheck",one); out.put("status","UP"); }
        catch(Exception e){ out.put("database","DOWN"); out.put("status","DOWN"); out.put("message",e.getMessage()); }
        return out;
    }
}
