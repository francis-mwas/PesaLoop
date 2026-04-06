package com.pesaloop.config;

import com.pesaloop.identity.adapters.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/actuator/info").permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/webhooks/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/groups/check-slug").permitAll()
                        // Swagger UI (dev/staging only — restrict in prod via firewall)
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html",
                                "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()

                        // Super admin only
                        .requestMatchers("/api/v1/admin/**").hasRole("SUPER_ADMIN")

                        // All other requests need authentication
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Allow your frontend origins
        config.setAllowedOrigins(Arrays.asList(
                "http://localhost:5173",   // Vite dev server
                "http://localhost:3000",   // React CRA / Next.js
                "http://localhost:4200",   // Angular
                "https://yourdomain.com"   // production frontend
        ));

        config.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        ));

        config.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "Accept"
        ));

        config.setAllowCredentials(true); // needed for JWT cookies/headers
        config.setMaxAge(3600L);          // cache preflight for 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}