package lccast.voting.system.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

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
                        .authenticationEntryPoint(
                                (request, response, authException) ->
                                        response.sendRedirect("/login")
                        )
                        .accessDeniedHandler(
                                (request, response, accessDeniedException) ->
                                        response.sendRedirect("/login")
                        )
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
}