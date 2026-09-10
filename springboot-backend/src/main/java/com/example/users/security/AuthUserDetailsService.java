package com.example.users.security;

import com.example.users.repository.AuthUserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
public class AuthUserDetailsService implements UserDetailsService {

    private final AuthUserRepository authUserRepository;

    public AuthUserDetailsService(AuthUserRepository authUserRepository) {
        this.authUserRepository = authUserRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return authUserRepository.findByUsernameIgnoreCase(username)
                .map(a -> User.withUsername(a.getUsername())
                        .password(a.getPasswordHash())
                        .authorities(authorities(a.getRoles()))
                        .disabled(!a.isEnabled())
                        .build())
                // DaoAuthenticationProvider converts this to BadCredentialsException and
                // still runs a dummy password check, so unknown-user and wrong-password
                // are indistinguishable in body, shape and timing (FR-6).
                .orElseThrow(() -> new UsernameNotFoundException("No such operator"));
    }

    private static List<SimpleGrantedAuthority> authorities(String roles) {
        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(r -> !r.isEmpty())
                .map(SimpleGrantedAuthority::new)
                .toList();
    }
}
