package narrative.engine.health;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import java.nio.file.Files;
import java.nio.file.Path;

@Readiness
@ApplicationScoped
public class WorldStorageHealthCheck implements HealthCheck {

    private final Path characters;
    private final Path personas;

    @Inject
    public WorldStorageHealthCheck(
            @ConfigProperty(name = "character.storage.path") Path characters,
            @ConfigProperty(name = "persona.storage.path") Path personas) {
        this.characters = characters;
        this.personas = personas;
    }

    @Override
    public HealthCheckResponse call() {
        boolean ok = readableDirectory(characters) && readableDirectory(personas);
        return HealthCheckResponse.named("world-storage")
                .status(ok)
                .withData("characters", characters.toString())
                .withData("personas", personas.toString())
                .build();
    }

    static boolean readableDirectory(Path path) {
        return path != null && Files.isDirectory(path) && Files.isReadable(path);
    }
}
