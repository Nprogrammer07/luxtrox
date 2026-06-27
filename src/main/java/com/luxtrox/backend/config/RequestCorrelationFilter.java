package com.luxtrox.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Le pone un "requestId" a cada peticion -- viaja en el MDC (asi
 * queda en CADA linea de log generada durante esa peticion, sin tener
 * que pasarlo a mano en cada logger.info(...)) y tambien en el header
 * de respuesta X-Request-Id, para correlacionar un reporte del
 * frontend con sus logs exactos del backend.
 *
 * A diferencia de JwtAuthenticationFilter, este SI puede ser
 * @Component sin problema: no esta envuelto en la cadena de Spring
 * Security via addFilterBefore en ningun lado, asi que no hay riesgo
 * del doble-registro que causo el bug de la Fase 8 (ver el comentario
 * de esa clase para el detalle completo).
 *
 * El MDC.clear() en el finally es obligatorio -- el servidor REUSA
 * hilos entre peticiones distintas; sin limpiarlo, el requestId de
 * ESTA peticion se quedaria pegado y aparaceria en los logs de la
 * SIGUIENTE peticion que le toque ese mismo hilo.
 */
@Component
public class RequestCorrelationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestCorrelationFilter.class);
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        long startedAt = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - startedAt;
            log.info("{} {} -> {} ({} ms)",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs);
            MDC.clear();
        }
    }
}