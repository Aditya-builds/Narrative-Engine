from world import _needed_layers, select_relevant_context


def _entity():
    return {
        "identity": {"name": "Aurora", "marker": "ID_LAYER"},
        "personality": {"marker": "PERS_LAYER"},
        "current_state": {"marker": "STATE_LAYER"},
        "abilities": {"marker": "ABIL_LAYER"},
        "relationships": {"marker": "REL_LAYER"},
        "appearance": {"marker": "LOOK_LAYER"},
        "equipment": {"marker": "GEAR_LAYER", "weapons": None, "armor": []},
    }


def test_normal_conversation_loads_default_layers():
    assert _needed_layers("hello") == ["identity", "personality", "relationships", "current_state"]
    text = select_relevant_context(_entity(), _entity(), "hello")
    assert "ID_LAYER" in text
    assert "PERS_LAYER" in text
    assert "REL_LAYER" in text
    assert "STATE_LAYER" in text
    assert "ABIL_LAYER" not in text
    assert "LOOK_LAYER" not in text
    assert "GEAR_LAYER" not in text


def test_combat_loads_state_and_abilities_not_appearance():
    assert _needed_layers("I attack with a spell") == [
        "identity",
        "personality",
        "current_state",
        "abilities",
    ]
    text = select_relevant_context(_entity(), _entity(), "I attack with a spell")
    assert "STATE_LAYER" in text
    assert "ABIL_LAYER" in text
    assert "LOOK_LAYER" not in text
    assert "REL_LAYER" not in text


def test_appearance_loads_look_and_equipment():
    text = select_relevant_context(_entity(), _entity(), "look at her hair and clothes")
    assert "LOOK_LAYER" in text
    assert "GEAR_LAYER" in text
    assert "ABIL_LAYER" not in text


def test_relationship_prompt_loads_relationships():
    text = select_relevant_context(_entity(), _entity(), "I insult you, you pathetic guild mage")
    assert "REL_LAYER" in text
    assert "ABIL_LAYER" not in text


def test_empty_equipment_fields_are_not_sent():
    text = select_relevant_context(_entity(), _entity(), "what armor do you wear")
    assert "GEAR_LAYER" in text
    assert "weapons" not in text
    assert "armor" not in text
