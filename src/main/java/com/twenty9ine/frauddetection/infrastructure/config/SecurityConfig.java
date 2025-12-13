package com.twenty9ine.frauddetection.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.audiences:fraud-detection-service}")
    private List<String> audiences;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.POST, "/fraud/assessments").hasAuthority("SCOPE_fraud:detect")
                        .requestMatchers(HttpMethod.GET, "/fraud/assessments", "/fraud/assessments/**").hasAuthority("SCOPE_fraud:read")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())
                                .decoder(jwtDecoder)
                        )
                )
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                );

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri) {

        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();

        // Add audience validator
        OAuth2TokenValidator<Jwt> audienceValidator = new AudienceValidator(audiences);
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> withAudience = new DelegatingOAuth2TokenValidator<>(withIssuer, audienceValidator);

        jwtDecoder.setJwtValidator(withAudience);

        return jwtDecoder;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwtGrantedAuthoritiesConverter());
        return converter;
    }

    @Bean
    public Converter<Jwt, Collection<GrantedAuthority>> jwtGrantedAuthoritiesConverter() {
        return new KeycloakGrantedAuthoritiesConverter();
    }

    /**
     * Custom converter to extract authorities fromDate Keycloak JWT token.
     * Extracts fromDate both:
     * 1. scope claim (space-delimited string)
     * 2. resource_access claim (nested client roles)
     * 3. realm_access claim (realm roles)
     */
    static class KeycloakGrantedAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

        private static final String SCOPE_CLAIM = "scope";
        private static final String RESOURCE_ACCESS_CLAIM = "resource_access";
        private static final String REALM_ACCESS_CLAIM = "realm_access";
        private static final String ROLES_CLAIM = "roles";
        private static final String SCOPE_PREFIX = "SCOPE_";
        private static final String ROLE_PREFIX = "ROLE_";

        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            Collection<GrantedAuthority> grantedAuthorities = new HashSet<>();

            // Extract scope authorities
            grantedAuthorities.addAll(extractScopeAuthorities(jwt));

            // Extract resource (client) role authorities
            grantedAuthorities.addAll(extractResourceAccessAuthorities(jwt));

            // Extract realm role authorities
            grantedAuthorities.addAll(extractRealmAccessAuthorities(jwt));

            return grantedAuthorities;
        }

        private Collection<GrantedAuthority> extractScopeAuthorities(Jwt jwt) {
            String scopes = jwt.getClaimAsString(SCOPE_CLAIM);
            if (scopes == null || scopes.isEmpty()) {
                return Collections.emptyList();
            }

            return Arrays.stream(scopes.split(" "))
                    .map(scope -> new SimpleGrantedAuthority(SCOPE_PREFIX + scope))
                    .collect(Collectors.toList());
        }

        private Collection<GrantedAuthority> extractResourceAccessAuthorities(Jwt jwt) {
            Map<String, Object> resourceAccess = jwt.getClaim(RESOURCE_ACCESS_CLAIM);

            if (resourceAccess == null || resourceAccess.isEmpty()) {
                return Collections.emptyList();
            }

            return resourceAccess.values().stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .filter(resource -> resource.containsKey(ROLES_CLAIM))
                    .flatMap(resource -> {
                        Object rolesObj = resource.get(ROLES_CLAIM);

                        if (rolesObj instanceof Collection) {
                            return ((Collection<?>) rolesObj).stream()
                                    .filter(String.class::isInstance)
                                    .map(String.class::cast)
                                    .map(role -> new SimpleGrantedAuthority(SCOPE_PREFIX + role));
                        }

                        return Stream.empty();
                    })
                    .collect(Collectors.toList());
        }

        private Collection<GrantedAuthority> extractRealmAccessAuthorities(Jwt jwt) {
            Map<String, Object> realmAccess = jwt.getClaim(REALM_ACCESS_CLAIM);

            if (realmAccess == null || !realmAccess.containsKey(ROLES_CLAIM)) {
                return Collections.emptyList();
            }

            Object rolesObj = realmAccess.get(ROLES_CLAIM);
            if (!(rolesObj instanceof Collection)) {
                return Collections.emptyList();
            }

            return ((Collection<?>) rolesObj).stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role))
                    .collect(Collectors.toList());
        }
    }

    /**
     * Custom validator to check JWT audience claim
     */
    static class AudienceValidator implements OAuth2TokenValidator<Jwt> {
        private final List<String> audiences;

        AudienceValidator(List<String> audiences) {
            this.audiences = audiences;
        }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt jwt) {
            List<String> tokenAudiences = jwt.getAudience();

            if (tokenAudiences == null || tokenAudiences.isEmpty()) {
                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "Token must contain audience", null)
                );
            }

            boolean hasValidAudience = tokenAudiences.stream()
                    .anyMatch(audiences::contains);

            if (hasValidAudience) {
                return OAuth2TokenValidatorResult.success();
            }

            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token",
                            "Token audience does not match expected audiences: " + audiences,
                            null)
            );
        }
    }
}