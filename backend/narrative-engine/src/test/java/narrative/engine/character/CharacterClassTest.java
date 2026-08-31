package narrative.engine.character;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CharacterClassTest {

    @Test
    void parsesMageIgnoringCaseAndWhitespace() {
        assertEquals(CharacterClass.MAGE, CharacterClass.from(" mage "));
        assertEquals("mage", CharacterClass.MAGE.id());
    }

    @Test
    void parsesMeleeAndTypo() {
        assertEquals(CharacterClass.MELEE, CharacterClass.from("Melee"));
        assertEquals(CharacterClass.MELEE, CharacterClass.from("melle"));
        assertEquals("melee", CharacterClass.MELEE.id());
    }

    @Test
    void rejectsBlankAndUnknown() {
        assertThrows(InvalidCharacterRequestException.class, () -> CharacterClass.from(null));
        assertThrows(InvalidCharacterRequestException.class, () -> CharacterClass.from("  "));
        assertThrows(InvalidCharacterRequestException.class, () -> CharacterClass.from("archer"));
    }
}
