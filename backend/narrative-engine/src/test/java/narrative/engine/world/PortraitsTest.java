package narrative.engine.world;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortraitsTest {

    @TempDir
    Path dir;

    @Test
    void privateConstructorIsInvocable() throws Exception {
        Constructor<Portraits> constructor = Portraits.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        constructor.newInstance();
    }

    @Test
    void findUsesCanonicalReferenceWhenTheFileExists() throws Exception {
        Path image = dir.resolve("face.png");
        Files.write(image, new byte[] {1, 2, 3});
        Files.writeString(dir.resolve("visual-identity.json"), "{\"canonicalReference\":\"face.png\"}");

        Optional<Path> found = Portraits.find(dir);

        assertTrue(found.isPresent());
        assertEquals(image.toAbsolutePath().normalize(), found.get());
    }

    @Test
    void findFallsBackToMainJpgWhenReferenceIsMissingOrRemote() throws Exception {
        Path fallback = dir.resolve("references").resolve("main.jpg");
        Files.createDirectories(fallback.getParent());
        Files.write(fallback, new byte[] {9, 8, 7});
        Files.writeString(dir.resolve("visual-identity.json"),
                "{\"canonicalReference\":\"https://example.com/remote.jpg\"}");

        assertEquals(fallback.toAbsolutePath().normalize(), Portraits.find(dir).orElseThrow());
    }

    @Test
    void findIgnoresBrokenVisualIdentityAndMissingFallback() throws Exception {
        Files.writeString(dir.resolve("visual-identity.json"), "{not-json");
        assertTrue(Portraits.find(dir).isEmpty());
    }

    @Test
    void insideRejectsBlankHttpTraversalAndMissingFiles() throws Exception {
        Path image = dir.resolve("ok.jpg");
        Files.write(image, new byte[] {1});

        assertTrue(Portraits.inside(dir, "").isEmpty());
        assertTrue(Portraits.inside(dir, "http://x").isEmpty());
        assertTrue(Portraits.inside(dir, "https://x").isEmpty());
        assertTrue(Portraits.inside(dir, "../secret.jpg").isEmpty());
        assertTrue(Portraits.inside(dir, "missing.jpg").isEmpty());
        assertEquals(image.toAbsolutePath().normalize(), Portraits.inside(dir, "ok.jpg").orElseThrow());
    }

    @Test
    void responseReturnsNotFoundThenImageBytes() throws Exception {
        try (Response missing = Portraits.response(Optional.empty())) {
            assertEquals(404, missing.getStatus());
        }

        Path image = dir.resolve("main.jpg");
        Files.write(image, new byte[] {1, 2, 3, 4});
        try (Response found = Portraits.response(Optional.of(image))) {
            assertEquals(200, found.getStatus());
            assertTrue(String.valueOf(found.getMediaType()).startsWith("image/"));
        }

        Path notes = dir.resolve("notes.txt");
        Files.writeString(notes, "not an image");
        try (Response coerced = Portraits.response(Optional.of(notes))) {
            assertEquals(200, coerced.getStatus());
            assertEquals("image/jpeg", coerced.getMediaType().toString());
        }
    }

    @Test
    void findFallsBackToWebpWhenJpgIsMissing() throws Exception {
        Path fallback = dir.resolve("references").resolve("main.webp");
        Files.createDirectories(fallback.getParent());
        Files.write(fallback, new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'});
        assertEquals(fallback.toAbsolutePath().normalize(), Portraits.find(dir).orElseThrow());
    }

    @Test
    void responseSniffsJpegPngAndWebpMagic() throws Exception {
        Path jpeg = dir.resolve("named.png");
        Files.write(jpeg, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00});
        try (Response found = Portraits.response(Optional.of(jpeg))) {
            assertEquals("image/jpeg", found.getMediaType().toString());
        }

        Path png = dir.resolve("named.jpg");
        Files.write(png, new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
        try (Response found = Portraits.response(Optional.of(png))) {
            assertEquals("image/png", found.getMediaType().toString());
        }

        Path webp = dir.resolve("named.gif");
        Files.write(webp, new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'});
        try (Response found = Portraits.response(Optional.of(webp))) {
            assertEquals("image/webp", found.getMediaType().toString());
        }
    }
}
