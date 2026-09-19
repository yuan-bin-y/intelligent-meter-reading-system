package com.byy.meterreading;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MeterReadingApplication {

    public static void main(String[] args) {
        SpringApplication.run(MeterReadingApplication.class, args);
    }
}
