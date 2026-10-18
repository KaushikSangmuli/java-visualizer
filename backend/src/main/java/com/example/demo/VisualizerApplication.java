package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point. Run this class (or `mvn spring-boot:run`) to start the API on port 8080. */
@SpringBootApplication
public class VisualizerApplication {
    public static void main(String[] args) {
        SpringApplication.run(VisualizerApplication.class, args);
    }
}
