package narrative.engine.character;

import java.util.Locale;

public enum CharacterClass {
    MAGE("mage"),
    MELEE("melee");

    private final String id;

    CharacterClass(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static CharacterClass from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidCharacterRequestException("Provide a class: mage or melee");
        }

        String value = raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "mage" -> MAGE;
            case "melee", "melle" -> MELEE;
            default -> throw new InvalidCharacterRequestException("Class must be mage or melee");
        };
    }
}
