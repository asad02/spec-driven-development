package com.example.users.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Mints the HS256 tokens the resource-server filter later validates. */
@Service
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final long expirySeconds;
    private final String issuer;

    public TokenService(JwtEncoder jwtEncoder,
                        @Value("${app.jwt.expiration-seconds:3600}") long expirySeconds,
                        @Value("${app.jwt.issuer:user-service}") String issuer) {
        this.jwtEncoder = jwtEncoder;
        this.expirySeconds = expirySeconds;
        this.issuer = issuer;
    }

    public String generate(Authentication authentication) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(now.plus(expirySeconds, ChronoUnit.SECONDS))
                .subject(authentication.getName())
                .claim("roles", roles(authentication))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /**
     * Only ROLE_* authorities. Spring Security 7 also grants authentication-factor
     * authorities (FACTOR_PASSWORD and friends); those are an internal detail and
     * must not leak into the login response, which the SPA and the Micronaut
     * service both expect to contain roles alone.
     */
    public List<String> roles(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .toList();
    }

    public long getExpirySeconds() {
        return expirySeconds;
    }
}
