package com.example.users;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SpringBootUserManagementApplication {
    public static void main(String[] args) {
        SpringApplication.run(SpringBootUserManagementApplication.class, args);
    }
}
