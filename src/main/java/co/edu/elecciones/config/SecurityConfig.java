package co.edu.elecciones.config;

import co.edu.elecciones.commons.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;

import java.util.*;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

 @Value("${app.cors.allowed-origins:http://localhost:4200,http://127.0.0.1:4200}")
 private String allowedOrigins;
 @Bean
 SecurityFilterChain security(
         HttpSecurity http,
         JwtAuthenticationFilter jwt
 ) throws Exception {

  return http
          .csrf(csrf -> csrf.disable())

          .headers(headers ->
                  headers.frameOptions(frame -> frame.sameOrigin())
          )

          .cors(cors ->
                  cors.configurationSource(cors())
          )

          .sessionManagement(session ->
                  session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
          )

          .authorizeHttpRequests(auth -> auth

                  .requestMatchers("/h2-console/**").permitAll()

                  .requestMatchers(
                          "/api/v1/auth/**",
                          "/api/v1/public/**",
                          "/api/v1/chat/**",
                          "/actuator/health",
                          "/swagger-ui/**",
                          "/v3/api-docs/**"
                  ).permitAll()

                  .requestMatchers(
                          HttpMethod.GET,
                          "/api/v1/partidos/**",
                          "/api/v1/candidatos/**",
                          "/api/v1/elecciones/**",
                          "/api/v1/encuestas/**",
                          "/api/v1/resultados/**",
                          "/api/v1/predicciones/**",
                          "/api/v1/reportes/**"
                  ).hasAnyRole("ADMINISTRADOR", "ANALISTA")

                  .requestMatchers(
                          "/api/v1/admin/**",
                          "/api/v1/usuarios/**",
                          "/api/v1/registraduria/**"
                  ).hasRole("ADMINISTRADOR")

                  .anyRequest().authenticated()
          )

          .addFilterBefore(
                  jwt,
                  UsernamePasswordAuthenticationFilter.class
          )

          .build();
 }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration c) throws Exception {
        return c.getAuthenticationManager();
    }

    private CorsConfigurationSource cors() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }
}
