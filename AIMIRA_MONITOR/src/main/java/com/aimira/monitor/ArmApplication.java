package com.aimira.monitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ArmApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArmApplication.class, args);
    }
}
