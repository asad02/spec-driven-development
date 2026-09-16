package com.example.users.exception;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(UUID id) {
        super("No user with id " + id + ".");
    }
}
