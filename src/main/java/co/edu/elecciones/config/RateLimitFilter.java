package co.edu.elecciones.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.*;

@Component
public class RateLimitFilter implements Filter {
    private record Bucket(AtomicIntegerWrapper count, long window) {
    }

    private static class AtomicIntegerWrapper {
        int value;
    }

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        String path = req.getRequestURI();
        if (path.startsWith("/api/v1/public") || path.startsWith("/api/v1/chat") || path.startsWith("/api/v1/resultados/live")) {
            String key = req.getRemoteAddr() + ":" + path;
            long window = Instant.now().getEpochSecond() / 60;
            Bucket b = buckets.compute(key, (k, old) -> old == null || old.window() != window ? new Bucket(new AtomicIntegerWrapper(), window) : old);
            synchronized (b.count()) {
                b.count().value++;
                if (b.count().value > 120) {
                    res.setStatus(429);
                    res.getWriter().write("Rate limit excedido");
                    return;
                }
            }
        }
        chain.doFilter(request, response);
    }
}
