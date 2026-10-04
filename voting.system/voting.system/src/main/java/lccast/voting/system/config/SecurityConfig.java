package lccast.voting.system.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.ConcurrentSessionControlAuthenticationStrategy;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.session.HttpSessionEventPublisher;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    // Required so SessionRegistry finds out when a session dies
    // (logout, browser close + timeout), otherwise it thinks stale
    // sessions are still active forever.
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    // Used manually in LoginController, since your login bypasses
    // the standard authentication filter chain.
    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy(SessionRegistry sessionRegistry) {
        ConcurrentSessionControlAuthenticationStrategy concurrentStrategy =
                new ConcurrentSessionControlAuthenticationStrategy(sessionRegistry);
        concurrentStrategy.setMaximumSessions(1);
        // true  = block the NEW login attempt if a session already exists
        // false = silently expire the OLD session and let the new login proceed
        concurrentStrategy.setExceptionIfMaximumExceeded(true);

        RegisterSessionAuthenticationStrategy registerStrategy =
                new RegisterSessionAuthenticationStrategy(sessionRegistry);

        return new CompositeSessionAuthenticationStrategy(
                List.of(concurrentStrategy, registerStrategy)
        );
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        http
                .authorizeHttpRequests(auth -> auth

                        // Public
                        .requestMatchers(
                                "/login",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/favicon.ico",
                                "/api/public/**",
                                "/forgot-password/**"
                        ).permitAll()

                        // Password-change gate — reachable by BOTH students and candidates
                        .requestMatchers(
                                "/voter/password-handler",
                                "/voter/change-password"
                        )
                        .hasAnyRole("STUDENT", "CANDIDATE")

                        // SUPERADMIN
                        .requestMatchers("/superadmin/**")
                        .hasRole("SUPERADMIN")

                        // ADMIN - Department type only
                        .requestMatchers("/admin-dept/**")
                        .hasAuthority("ROLE_ADMIN_DEPARTMENT")

                        // ADMIN - SSC type only
                        .requestMatchers("/admin-ssc/**")
                        .hasAuthority("ROLE_ADMIN_SSC")

                        // CANDIDATE
                        .requestMatchers("/candidate/**")
                        .hasRole("CANDIDATE")

                        // STUDENT
                        .requestMatchers("/voter/**")
                        .hasAnyRole("STUDENT", "CANDIDATE")

                        // Everything else requires login
                        .anyRequest()
                        .authenticated()
                )

                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            System.out.println("=== NOT AUTHENTICATED === " + request.getMethod() + " " + request.getRequestURI());
                            response.sendRedirect("/login");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            System.out.println("=== ACCESS DENIED (wrong role) === " + request.getMethod() + " " + request.getRequestURI());
                            response.sendError(403, "Access denied");
                        })
                )

                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                )

                .csrf(csrf -> csrf.disable());

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}