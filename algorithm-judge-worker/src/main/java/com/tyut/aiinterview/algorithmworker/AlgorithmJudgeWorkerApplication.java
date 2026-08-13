package com.tyut.aiinterview.algorithmworker;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("com.tyut.aiinterview.algorithmworker.mapper")
public class AlgorithmJudgeWorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(AlgorithmJudgeWorkerApplication.class, args);
    }
}
