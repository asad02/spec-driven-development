package com.example.users.service;

import com.example.users.entity.AuthUserEntity;
import com.example.users.repository.AuthUserRepository;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.AuthenticationFailureReason;
import io.micronaut.security.authentication.AuthenticationRequest;
import io.micronaut.security.authentication.AuthenticationResponse;
import io.micronaut.security.authentication.provider.HttpRequestAuthenticationProvider;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import org.mindrot.jbcrypt.BCrypt;

import java.util.Optional;

/**
 * Authenticates an operator against the {@code auth_user} table.
 *
 * <p>Every failure path returns the same generic reason (FR-6): the caller can
 * never tell an unknown username from a bad password from a disabled account.
 */
@Singleton
public class DbAuthenticationProvider<B> implements HttpRequestAuthenticationProvider<B> {

    /**
     * A real BCrypt hash of a value nobody holds. When the username is unknown
     * we still verify against this, so a missing account costs the same time as
     * a wrong password and response timing cannot be used to enumerate users.
     */
    private static final String DUMMY_HASH =
            "$2a$12$Mjn5tG35gW9ile9m36arguWG9KBqUnh0LzDTjh4HD9OM9JlQtAguO";

    private final AuthUserRepository authUserRepository;

    public DbAuthenticationProvider(AuthUserRepository authUserRepository) {
        this.authUserRepository = authUserRepository;
    }

    @Override
    @Transactional
    public AuthenticationResponse authenticate(@Nullable HttpRequest<B> requestContext,
                                               AuthenticationRequest<String, String> authRequest) {
        String identity = authRequest.getIdentity();
        String secret = authRequest.getSecret();

        if (identity == null || secret == null || secret.isEmpty()) {
            return AuthenticationResponse.failure(AuthenticationFailureReason.CREDENTIALS_DO_NOT_MATCH);
        }

        Optional<AuthUserEntity> found = authUserRepository.findByUsernameIgnoreCase(identity);

        if (found.isEmpty()) {
            BCrypt.checkpw(secret, DUMMY_HASH);
            return AuthenticationResponse.failure(AuthenticationFailureReason.CREDENTIALS_DO_NOT_MATCH);
        }

        AuthUserEntity account = found.get();
        boolean matches = checkPassword(secret, account.getPasswordHash());

        if (!matches || !account.isEnabled()) {
            return AuthenticationResponse.failure(AuthenticationFailureReason.CREDENTIALS_DO_NOT_MATCH);
        }

        return AuthenticationResponse.success(account.getUsername(), account.roleList());
    }

    private static boolean checkPassword(String candidate, String hash) {
        if (hash == null || hash.isBlank()) {
            return false;
        }
        try {
            return BCrypt.checkpw(candidate, hash);
        } catch (IllegalArgumentException ex) {
            // A malformed stored hash must fail closed, not blow up the request.
            return false;
        }
    }
}
