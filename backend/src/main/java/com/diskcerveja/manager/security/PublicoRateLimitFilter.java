package com.diskcerveja.manager.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rate limit simples em memória para endpoints públicos sensíveis (anti-spam / enumeração).
 */
@Component
public class PublicoRateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000L;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/publico/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();
        int limit = resolveLimit(method, path);
        if (limit <= 0) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = clientKey(request) + "|" + method + "|" + bucket(path);
        if (!allow(key, limit)) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"erro\":\"Muitas tentativas. Aguarde um minuto e tente de novo.\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static int resolveLimit(String method, String path) {
        if ("POST".equalsIgnoreCase(method) && path.endsWith("/pedidos")) {
            return 8;
        }
        if ("POST".equalsIgnoreCase(method) && path.contains("/confirmar-pagamento")) {
            return 20;
        }
        if ("GET".equalsIgnoreCase(method) && path.contains("/status-pagamento")) {
            return 60;
        }
        if ("GET".equalsIgnoreCase(method) && path.endsWith("/frete")) {
            return 60;
        }
        if ("POST".equalsIgnoreCase(method) && path.contains("/infinitepay/webhook")) {
            return 120;
        }
        return 0;
    }

    private static String bucket(String path) {
        if (path.contains("/confirmar-pagamento")) {
            return "confirm";
        }
        if (path.contains("/status-pagamento")) {
            return "status";
        }
        if (path.endsWith("/frete")) {
            return "frete";
        }
        if (path.contains("/infinitepay/webhook")) {
            return "webhook";
        }
        if (path.endsWith("/pedidos")) {
            return "pedidos";
        }
        return "other";
    }

    private boolean allow(String key, int limit) {
        long now = Instant.now().toEpochMilli();
        prune(now);
        Window w = windows.compute(key, (k, prev) -> {
            if (prev == null || now - prev.startMs >= WINDOW_MS) {
                return new Window(now, new AtomicInteger(1));
            }
            prev.count.incrementAndGet();
            return prev;
        });
        return w.count.get() <= limit;
    }

    private void prune(long now) {
        if (windows.size() < 2_000) {
            return;
        }
        Iterator<Map.Entry<String, Window>> it = windows.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Window> e = it.next();
            if (now - e.getValue().startMs >= WINDOW_MS * 2) {
                it.remove();
            }
        }
    }

    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }

    private static final class Window {
        final long startMs;
        final AtomicInteger count;

        Window(long startMs, AtomicInteger count) {
            this.startMs = startMs;
            this.count = count;
        }
    }
}
