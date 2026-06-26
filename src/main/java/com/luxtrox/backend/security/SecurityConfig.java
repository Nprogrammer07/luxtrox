package com.luxtrox.backend.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Reglas de seguridad de toda la API. Stateless -- nada de sesiones de
 * servidor, toda la autenticacion va por JWT en el header Authorization.
 *
 * CSRF desactivado a proposito: CSRF protege contra ataques que abusan
 * de autenticacion por COOKIE de sesion; esta API no usa cookies para
 * autenticar (solo Bearer tokens en el header), asi que CSRF no aplica
 * aqui -- mantenerlo activo solo agregaria friccion sin proteger nada
 * real.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    public SecurityConfig(JwtService jwtService,
                           CustomUserDetailsService userDetailsService,
                           PasswordEncoder passwordEncoder) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Sin esto, Spring Security usa Http403ForbiddenEntryPoint
            // por defecto para CUALQUIER acceso sin autenticar -- 403
            // en vez del 401 convencional. accessDeniedHandler queda
            // explicito tambien (aunque su default ya era 403) para
            // que ninguno de los dos casos depender de un default
            // implicito.
            .exceptionHandling(exceptions -> exceptions
                    .authenticationEntryPoint((request, response, authException) ->
                            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "No autenticado"))
                    .accessDeniedHandler((request, response, accessDeniedException) ->
                            response.sendError(HttpServletResponse.SC_FORBIDDEN, "No autorizado")))
            .authorizeHttpRequests(auth -> auth
                // Publico: registro/login/refresh y documentacion.
                .requestMatchers("/auth/**").permitAll()
                .requestMatchers("/docs/**", "/api-docs/**", "/swagger-ui/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // Spring Boot reenvia internamente a /error cuando se
                // llama sendError() (lo que hacen authenticationEntryPoint
                // y accessDeniedHandler de aqui abajo). JwtAuthenticationFilter
                // se autoexcluye de ese forward (OncePerRequestFilter,
                // shouldNotFilterErrorDispatch() = true por defecto), asi
                // que sin esto /error vuelve a pasar por la cadena SIN
                // autenticar, cae en anyRequest().authenticated(), y
                // pisa el codigo de estado original con un 401 -- esto
                // es lo que causaba que un 403 real (rol insuficiente)
                // le llegara al cliente como 401 (encontrado con
                // @EnableWebSecurity(debug=true) en la Fase 8).
                .requestMatchers("/error").permitAll()
                // Callbacks de proveedores externos (NOWPayments) -- no
                // mandan JWT, se autentican con su propia firma HMAC
                // (ver NowPaymentsSignatureVerifier). El controller
                // mismo rechaza cualquier firma invalida.
                .requestMatchers("/webhooks/**").permitAll()
                // Agrupacion de endpoints segun el prompt maestro:
                // /admin requiere rol ADMIN explicitamente.
                .requestMatchers("/admin/**").hasRole("ADMIN")
                // El resto (/users, /positions, /cashback, /referrals,
                // /withdrawals, /payments, /invoices) solo exige estar
                // autenticado, sin importar el rol.
                .anyRequest().authenticated()
            )
            .authenticationProvider(authenticationProvider())
            // Construido aqui DIRECTAMENTE (new, no inyectado) -- ver
            // el comentario de la clase para el porque exacto.
            .addFilterBefore(new JwtAuthenticationFilter(jwtService, userDetailsService),
                    UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}