package com.example.featuretoggle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * The feature toggle service. It owns the `feature-toggle` database and is the
 * only service in the system that depends on ff4j; everything else asks it over
 * HTTP.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
