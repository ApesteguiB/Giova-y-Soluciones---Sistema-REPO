package pe.giova.submissions;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestSizeFilter extends OncePerRequestFilter {
    private static final long MAX_SUBMISSION_BYTES = 32_768;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        long length = request.getContentLengthLong();
        if ((path.endsWith("/contact-submissions") || path.endsWith("/claims")) && length > MAX_SUBMISSION_BYTES) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Solicitud demasiado grande");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
