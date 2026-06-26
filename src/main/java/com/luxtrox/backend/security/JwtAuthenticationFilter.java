package com.luxtrox.backend.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Por cada request: si viene un header "Authorization: Bearer <token>"
 * con un access token valido, carga al usuario y lo deja en el
 * SecurityContext para el resto de la cadena de filtros/controladores.
 * Si no viene token, o es invalido, simplemente deja pasar el request
 * sin autenticar -- seran las reglas de SecurityConfig las que decidan
 * si esa ruta requiere autenticacion o no.
 *
 * A PROPOSITO no tiene @Component: si lo tuviera, Spring Boot lo
 * auto-registraria TAMBIEN como filtro de servlet generico (fuera de
 * la cadena de Spring Security, en un momento distinto del pipeline),
 * ademas de quedar cableado explicitamente en SecurityConfig via
 * addFilterBefore. Esa doble ejecucion pisaba el SecurityContext que
 * la cadena de seguridad real necesitaba, y por eso hasta un token
 * valido terminaba rechazado (encontrado por los tests de API de la
 * Fase 8 -- ningun test anterior ejercitaba el filtro real via HTTP).
 * SecurityConfig debe construirlo directamente (new), nunca como bean.
 *
 * Por ser OncePerRequestFilter, este filtro se AUTOEXCLUYE de los
 * forwards internos a /error (shouldNotFilterErrorDispatch() = true
 * por defecto) -- por eso /error esta en permitAll() en SecurityConfig,
 * sin eso un 403/401 real terminaba pisado por un 401 generado en esa
 * segunda pasada sin autenticar (tambien encontrado en la Fase 8).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, CustomUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        if (jwtService.isAccessTokenValid(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
            Claims claims = jwtService.parseClaims(token);
            String email = claims.get("email", String.class);

            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            if (userDetails.isEnabled() && userDetails.isAccountNonLocked()) {
                var authToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        filterChain.doFilter(request, response);
    }
}