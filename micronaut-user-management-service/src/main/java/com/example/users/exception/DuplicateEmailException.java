package com.example.users.exception;

public class DuplicateEmailException extends RuntimeException {

    private final String email;

    public DuplicateEmailException(String email) {
        super("Email address is already in use.");
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
