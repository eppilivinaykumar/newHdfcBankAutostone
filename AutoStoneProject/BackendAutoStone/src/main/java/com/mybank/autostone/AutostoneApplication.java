package com.mybank.autostone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AutostoneApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutostoneApplication.class, args);
    }

}
