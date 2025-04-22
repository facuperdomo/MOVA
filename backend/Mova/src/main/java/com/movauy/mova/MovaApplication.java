package com.movauy.mova;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MovaApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(MovaApplication.class);
        app.setAdditionalProfiles("local");
        app.run(args);
    }
}
