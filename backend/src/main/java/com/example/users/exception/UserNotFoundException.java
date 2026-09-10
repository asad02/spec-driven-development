package com.example.users.exception;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {

    private final UUID id;

    public UserNotFoundException(UUID id) {
        super("User " + id + " was not found.");
        this.id = id;
    }

    public UUID getId() {
        return id;
    }
}
