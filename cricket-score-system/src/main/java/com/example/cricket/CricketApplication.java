package com.example.cricket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CricketApplication {
    public static void main(String[] args) {
        SpringApplication.run(CricketApplication.class, args);
    }
}
