package narrative.engine.api;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.Provider;

import java.io.File;

@Provider
public class ErrorEnvelopeFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        int status = response.getStatus();
        if (status < 400) {
            return;
        }
        if (isBinary(response)) {
            return;
        }
        String path = request.getUriInfo().getPath();
        if (path != null && path.contains("portrait") && response.getEntity() == null) {
            return;
        }
        Object entity = response.getEntity();
        if (ApiErrors.alreadyEnveloped(entity)) {
            return;
        }
        response.setEntity(ApiErrors.body(status, requestPath(request), ApiErrors.messageFrom(entity)));
        response.getHeaders().putSingle("Content-Type", MediaType.APPLICATION_JSON);
    }

    static String requestPath(ContainerRequestContext request) {
        String path = request.getUriInfo().getPath();
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private static boolean isBinary(ContainerResponseContext response) {
        if (response.getEntity() instanceof File) {
            return true;
        }
        MediaType type = response.getMediaType();
        return type != null && "image".equalsIgnoreCase(type.getType());
    }
}
