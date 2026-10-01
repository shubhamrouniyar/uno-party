package com.unoparty;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class UnoPartyApplication {

    public static void main(String[] args) {
        SpringApplication.run(UnoPartyApplication.class, args);
    }
}
