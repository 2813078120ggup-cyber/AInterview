package com.tyut.aiinterview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AiInterviewApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiInterviewApplication.class, args);
    }
}
