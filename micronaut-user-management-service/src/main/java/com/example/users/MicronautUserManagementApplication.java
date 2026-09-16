package com.example.users;

import io.micronaut.runtime.Micronaut;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

@OpenAPIDefinition(
        info = @Info(
                title = "User Management Service",
                version = "1.0",
                description = "Create, update, read and search user records. "
                        + "All user endpoints require a bearer token obtained from /api/v1/auth/login.",
                contact = @Contact(name = "Platform team")
        )
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class MicronautUserManagementApplication {

    public static void main(String[] args) {
        Micronaut.run(MicronautUserManagementApplication.class, args);
    }
}
