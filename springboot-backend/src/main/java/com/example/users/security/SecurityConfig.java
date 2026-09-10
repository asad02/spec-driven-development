package com.example.users.security;

import com.example.users.dto.ApiError;
import com.example.users.config.CorrelationIdFilter;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.spec.SecretKeySpec;
import java.util.List;

@Configuration
public class SecurityConfig {

    private final byte[] secret;

    public SecurityConfig(@Value("${app.jwt.secret}") String secret) {
        this.secret = secret.getBytes();
    }

    private SecretKeySpec key() {
        return new SecretKeySpec(secret, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key()));
    }

    @Bean
    JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(key()).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        // Cost 12 — must match the hashes already in auth_user, written by jBCrypt.
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        http
            .csrf(csrf -> csrf.disable())                 // stateless bearer tokens, no cookies
            .cors(org.springframework.security.config.Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(b -> b.disable())
            .formLogin(f -> f.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                // nginx calls this as an auth_request subrequest with no credentials.
                .requestMatchers(HttpMethod.GET, "/api/v1/features/route").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/features").permitAll()
                .requestMatchers("/actuator/health/**", "/health/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(new RolesClaimConverter()))
                .authenticationEntryPoint((req, res, ex) ->
                        write(res, objectMapper, 401, "UNAUTHENTICATED", "Authentication required."))
                .accessDeniedHandler((req, res, ex) ->
                        write(res, objectMapper, 403, "FORBIDDEN", "You do not have access to this resource.")))
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) ->
                        write(res, objectMapper, 401, "UNAUTHENTICATED", "Authentication required."))
                .accessDeniedHandler((req, res, ex) ->
                        write(res, objectMapper, 403, "FORBIDDEN", "You do not have access to this resource.")));
        return http.build();
    }

    /**
     * Returns the documented ApiError envelope rather than Spring's default empty
     * 401 body — see technical-spec-springboot §6.3, a deliberate divergence from
     * the Micronaut service's framework-default response.
     */
    private static void write(jakarta.servlet.http.HttpServletResponse response,
                              ObjectMapper objectMapper,
                              int status, String code, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String correlationId = org.slf4j.MDC.get(CorrelationIdFilter.MDC_KEY);
        response.getWriter().write(objectMapper.writeValueAsString(ApiError.of(code, message, correlationId)));
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origin:http://localhost:4200}") String allowedOrigin) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id"));
        config.setExposedHeaders(List.of("X-Correlation-Id", "X-Served-By"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
