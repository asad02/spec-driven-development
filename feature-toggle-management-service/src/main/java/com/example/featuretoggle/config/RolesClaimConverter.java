package com.example.featuretoggle.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/**
 * Reads authorities from the `roles` claim the product backends mint, rather than
 * the `scope`/`scp` claims Spring's default converter expects. Every service in the
 * system must agree on this claim name.
 */
public class RolesClaimConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        List<SimpleGrantedAuthority> authorities = roles == null
                ? List.of() : roles.stream().map(SimpleGrantedAuthority::new).toList();
        return new UsernamePasswordAuthenticationToken(jwt.getSubject(), jwt, authorities);
    }
}
