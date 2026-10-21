
package com.krednova.payroll.config;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import com.krednova.payroll.security.ActiveUserFilter;
import com.krednova.payroll.security.RateLimitFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(
            HttpSecurity http,
            JdbcClient jdbc,
            @Value("${krednova.login-rate-limit:10}") int rateLimit
    ) throws Exception {

        http
            // Allow requests from the deployed frontend.
            .cors(Customizer.withDefaults())

            // Keep CSRF protection enabled.
            .csrf(c -> c
                .csrfTokenRepository(
                    CookieCsrfTokenRepository.withHttpOnlyFalse()
                )
                .csrfTokenRequestHandler(
                    new CsrfTokenRequestAttributeHandler()
                )
            )

            .addFilterBefore(
                new RateLimitFilter(rateLimit),
                CsrfFilter.class
            )

            .addFilterAfter(
                new CsrfCookieFilter(),
                BasicAuthenticationFilter.class
            )

            .addFilterBefore(
                new ActiveUserFilter(jdbc),
                AnonymousAuthenticationFilter.class
            )

            .authorizeHttpRequests(a -> a
                .requestMatchers(
                    "/api/v1/auth/csrf",
                    "/api/v1/auth/login",
                    "/api/v1/auth/setup-status",
                    "/api/v1/auth/setup"
                ).permitAll()
                .anyRequest().authenticated()
            )

            .exceptionHandling(e -> e
                .authenticationEntryPoint(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)
                )
            )

            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)

            .headers(h -> h
                .contentSecurityPolicy(c ->
                    c.policyDirectives(
                        "default-src 'none'; frame-ancestors 'none'"
                    )
                )
                .httpStrictTransportSecurity(x ->
                    x.includeSubDomains(true)
                     .maxAgeInSeconds(31536000)
                )
                .referrerPolicy(r ->
    r.policy(
        org.springframework.security.web.header.writers
            .ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER
    )
)
                .permissionsPolicy(x ->
                    x.policy(
                        "geolocation=(), microphone=(), camera=(), payment=()"
                    )
                )
            );

        return http.build();
    }

    /**
     * CORS configuration for the deployed React frontend.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(List.of(
            "https://krednova-app-37ps.vercel.app"
        ));

        config.setAllowedMethods(List.of(
            "GET",
            "POST",
            "PUT",
            "PATCH",
            "DELETE",
            "OPTIONS"
        ));

        config.setAllowedHeaders(List.of(
            "Authorization",
            "Content-Type",
            "X-XSRF-TOKEN",
            "X-Requested-With",
            "Accept"
        ));

        // Required for browser session cookies.
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
            new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", config);

        return source;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration
    ) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * Ensures the CSRF token is accessed so its cookie can be written.
     */
    static final class CsrfCookieFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(
                HttpServletRequest req,
                HttpServletResponse res,
                FilterChain chain
        ) throws ServletException, IOException {

            CsrfToken token =
                (CsrfToken) req.getAttribute("_csrf");

            if (token != null) {
                token.getToken();
            }

            chain.doFilter(req, res);
        }
    }
}
