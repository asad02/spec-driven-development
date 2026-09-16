package com.example.users.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI userManagementOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("User Management Service (Spring Boot)")
                        .description("""
                                Create, update, read and search user records. All user endpoints \
                                require a bearer token obtained from /api/v1/auth/login.

                                Unlike the Micronaut implementation, the login endpoint is a real \
                                controller and therefore appears in this document — so the spec is \
                                importable and usable on its own.""")
                        .version("1.0")
                        .contact(new Contact().name("Platform team")))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
