package com.ticketforge.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import com.ticketforge.user.UserRepository;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  JwtEncoder jwtEncoder(@Value("${ticketforge.jwt.secret}") String secret) {
    validateSecret(secret);
    var key =
        new SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
    return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(key));
  }

  @Bean
  JwtDecoder jwtDecoder(
      @Value("${ticketforge.jwt.secret}") String secret,
      @Value("${ticketforge.jwt.issuer}") String issuer,
      UserRepository users) {
    validateSecret(secret);
    var key =
        new SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    OAuth2TokenValidator<Jwt> tokenVersion =
        jwt -> {
          try {
            var user = users.findById(java.util.UUID.fromString(jwt.getSubject())).orElse(null);
            Number version = jwt.getClaim("ver");
            if (user != null && version != null && version.intValue() == user.getTokenVersion()) {
              return OAuth2TokenValidatorResult.success();
            }
          } catch (RuntimeException ignored) {
            // Invalid subjects and claims are rejected below without exposing details.
          }
          return OAuth2TokenValidatorResult.failure(
              new OAuth2Error("invalid_token", "Token has been revoked", null));
        };
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer), tokenVersion));
    return decoder;
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http) throws Exception {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          var roles = jwt.getClaimAsStringList("roles");
          return roles == null
              ? java.util.List.of()
              : roles.stream()
                  .<org.springframework.security.core.GrantedAuthority>map(
                      r ->
                          new org.springframework.security.core.authority.SimpleGrantedAuthority(
                              "ROLE_" + r))
                  .toList();
        });
    // API mutations require an Authorization bearer token and never use cookie authentication.
    return http.csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
        .headers(
            headers ->
                headers
                    .contentSecurityPolicy(
                        csp ->
                            csp.policyDirectives(
                                "default-src 'self'; style-src 'self' https://fonts.googleapis.com; font-src https://fonts.gstatic.com; img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'"))
                    .permissionsPolicyHeader(
                        policy ->
                            policy.policy("camera=(), microphone=(), geolocation=(), payment=()")))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/",
                        "/index.html",
                        "/assets/**",
                        "/favicon.svg",
                        "/api/v1/auth/**",
                        "/api/v1/payments/stripe/webhook",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/actuator/health/**")
                    .permitAll()
                    .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/events/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter)))
        .build();
  }

  private void validateSecret(String secret) {
    if (secret == null || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
      throw new IllegalStateException("JWT_SECRET must contain at least 32 bytes");
    }
  }
}
