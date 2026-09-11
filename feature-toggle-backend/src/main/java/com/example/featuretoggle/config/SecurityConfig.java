package com.example.featuretoggle.config;

import com.example.featuretoggle.api.ApiError;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;
import java.util.List;

/**
 * This service validates tokens but never issues them — there is no login
 * endpoint here. It shares the signing secret with the product backends, so an
 * operator signs in once and that token works everywhere.
 */
@Configuration
public class SecurityConfig {

    private final byte[] secret;

    public SecurityConfig(@Value("${app.jwt.secret}") String secret) {
        this.secret = secret.getBytes();
    }

    @Bean
    JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(new SecretKeySpec(secret, "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(b -> b.disable())
            .formLogin(f -> f.disable())
            .authorizeHttpRequests(auth -> auth
                // nginx calls this as an anonymous auth_request subrequest.
                .requestMatchers(HttpMethod.GET, "/api/v1/features/route").permitAll()
                .requestMatchers("/health/**", "/actuator/health/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(new RolesClaimConverter()))
                .authenticationEntryPoint((req, res, ex) ->
                        write(res, objectMapper, 401, "UNAUTHENTICATED", "Authentication required.")))
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) ->
                        write(res, objectMapper, 401, "UNAUTHENTICATED", "Authentication required.")));
        return http.build();
    }

    private static void write(jakarta.servlet.http.HttpServletResponse response, ObjectMapper objectMapper,
                              int status, String code, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiError.of(code, message, org.slf4j.MDC.get(CorrelationIdFilter.MDC_KEY))));
    }

    /**
     * Every browser origin needs an entry, including ones reaching this service
     * through a same-origin nginx proxy: the browser still sends `Origin` on
     * POST/PUT/DELETE, and an unlisted origin is refused before any controller runs.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:4300}") String allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(o -> !o.isEmpty()).toList());
        // DELETE is required by the admin API (revoke role, remove property, delete).
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id"));
        config.setExposedHeaders(List.of("X-Correlation-Id", "X-Backend-Host"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
