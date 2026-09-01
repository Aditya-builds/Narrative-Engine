package narrative.engine.world;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class Portraits {

    private Portraits() {}

    public static Optional<Path> find(Path entityDirectory) {
        Path dir = entityDirectory.toAbsolutePath().normalize();
        Path visual = dir.resolve("visual-identity.json");
        if (Files.isRegularFile(visual)) {
            try {
                JsonNode node = new ObjectMapper().readTree(visual.toFile());
                Optional<Path> fromRef = inside(dir, node.path("canonicalReference").asText(""));
                if (fromRef.isPresent()) {
                    return fromRef;
                }
            } catch (IOException ignored) {
                // fall through to default file
            }
        }
        Path references = dir.resolve("references");
        for (String name : new String[] {"main.jpg", "main.webp", "main.png"}) {
            Path fallback = references.resolve(name);
            if (Files.isRegularFile(fallback)) {
                return Optional.of(fallback);
            }
        }
        return Optional.empty();
    }

    public static Response response(Optional<Path> file) {
        if (file.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        Path path = file.get();
        try {
            return Response.ok(path.toFile()).type(imageType(path)).build();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read portrait", e);
        }
    }

    static String imageType(Path path) throws IOException {
        try (var in = Files.newInputStream(path)) {
            byte[] head = in.readNBytes(12);
            if (head.length >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8) {
                return "image/jpeg";
            }
            if (head.length >= 8
                    && (head[0] & 0xFF) == 0x89
                    && head[1] == 0x50
                    && head[2] == 0x4E
                    && head[3] == 0x47) {
                return "image/png";
            }
            if (head.length >= 12
                    && head[0] == 'R'
                    && head[1] == 'I'
                    && head[2] == 'F'
                    && head[3] == 'F'
                    && head[8] == 'W'
                    && head[9] == 'E'
                    && head[10] == 'B'
                    && head[11] == 'P') {
                return "image/webp";
            }
        }
        String probed = Files.probeContentType(path);
        if (probed != null && probed.startsWith("image/")) {
            return probed;
        }
        return "image/jpeg";
    }

    static Optional<Path> inside(Path dir, String relative) {
        if (relative == null
                || relative.isBlank()
                || relative.startsWith("http://")
                || relative.startsWith("https://")
                || relative.contains("..")) {
            return Optional.empty();
        }
        Path candidate = dir.resolve(relative).normalize();
        if (!candidate.startsWith(dir) || !Files.isRegularFile(candidate)) {
            return Optional.empty();
        }
        return Optional.of(candidate);
    }
}
