from langchain_core.tools import tool

from constants import EQUIPMENT_SLOTS, ENTITY_TYPES
from tools.rules import clamp_score, relationship_delta, require_stat, stat_delta
from world import WorldApiError, domain_file, get_entity, runtime_context, update_entity


def _kind(entity_type: str) -> str:
    value = (entity_type or "").strip().lower()
    if value not in ENTITY_TYPES:
        raise WorldApiError("entity_type must be 'character' or 'persona'")
    return value


@tool
def get_character(name: str) -> str:
    """Load the Character runtime context from Quarkus. Does not include visual-identity."""
    try:
        return str(runtime_context(get_entity("character", name.strip())))
    except Exception as exc:
        return f"Error: {exc}"


@tool
def get_persona(name: str) -> str:
    """Load the Persona runtime context from Quarkus. Does not include visual-identity."""
    try:
        return str(runtime_context(get_entity("persona", name.strip())))
    except Exception as exc:
        return f"Error: {exc}"


@tool
def record_relationship_event(entity_type: str, name: str, other_name: str, event: str, severity: str) -> str:
    """Apply a relationship event. You choose what happened; code chooses the numeric delta.

    event: compliment, kindness, defended_guild, fought_together, saved_life, promise,
           insult, mockery, guild_insult, betrayal, attack
    severity: minor or major
    """
    try:
        kind = _kind(entity_type)
        delta = relationship_delta(event, severity)
        target = other_name.strip().lower()
        entity = get_entity(kind, name.strip())
        current = domain_file(entity, "relationships").get("relationships") or {}
        previous = int(current.get(target) or 50)
        next_score = clamp_score(previous + delta)
        update_entity(kind, name.strip(), {"relationships": {target: next_score}})
        return f"CHANGE: {kind} {name} {event}/{severity} toward {target}: {previous} -> {next_score} (delta {delta})"
    except Exception as exc:
        return f"Error: {exc}"


@tool
def record_stat_event(entity_type: str, name: str, attribute: str, event: str, severity: str) -> str:
    """Apply a bounded stat event. You choose what happened; code chooses the numeric delta.

    attribute: vigor, mind, endurance, strength, dexterity, intelligence, faith, arcane
    event: injury, exhaustion, recover, training
    severity: minor or major
    """
    try:
        kind = _kind(entity_type)
        attr = require_stat(attribute)
        delta = stat_delta(event, severity)
        entity = get_entity(kind, name.strip())
        attributes = dict((domain_file(entity, "stats").get("attributes") or {}))
        previous = int(attributes.get(attr) or 0)
        next_value = clamp_score(previous + delta)
        update_entity(kind, name.strip(), {"stats": {"attributes": {attr: next_value}}})
        return f"CHANGE: {kind} {name} {attr} {event}/{severity}: {previous} -> {next_value} (delta {delta})"
    except Exception as exc:
        return f"Error: {exc}"


@tool
def add_equipment(entity_type: str, name: str, slot: str, item: str) -> str:
    """Add an item to weapons, armor, or accessories."""
    try:
        return _patch_equipment(entity_type, name, slot, item, remove=False)
    except Exception as exc:
        return f"Error: {exc}"


@tool
def remove_equipment(entity_type: str, name: str, slot: str, item: str) -> str:
    """Remove an item from weapons, armor, or accessories."""
    try:
        return _patch_equipment(entity_type, name, slot, item, remove=True)
    except Exception as exc:
        return f"Error: {exc}"


@tool
def remember_event(fact: str) -> str:
    """Store a lasting story fact (a promise, injury, secret, or relationship turning point)."""
    cleaned = " ".join((fact or "").split())
    if not cleaned:
        return "Error: fact was empty"
    return f"MEMORY:{cleaned}"


def _patch_equipment(entity_type: str, name: str, slot: str, item: str, remove: bool) -> str:
    kind = _kind(entity_type)
    slot_name = slot.strip().lower()
    if slot_name not in EQUIPMENT_SLOTS:
        return f"Error: slot must be one of {', '.join(EQUIPMENT_SLOTS)}"
    item_name = item.strip()
    if not item_name:
        return "Error: item was empty"
    entity = get_entity(kind, name.strip())
    equipment = dict(domain_file(entity, "equipment"))
    items = [str(entry) for entry in (equipment.get(slot_name) or [])]
    lowered = item_name.lower()
    if remove:
        next_items = [entry for entry in items if entry.lower() != lowered]
        if len(next_items) == len(items):
            return f"Error: '{item_name}' was not in {slot_name}"
    else:
        if any(entry.lower() == lowered for entry in items):
            return f"{item_name} is already in {slot_name}"
        next_items = [*items, item_name]
    update_entity(kind, name.strip(), {"equipment": {slot_name: next_items}})
    action = "removed" if remove else "added"
    return f"CHANGE: {kind} {name} {action} {item_name} on {slot_name}"


TOOLS = [
    get_character,
    get_persona,
    record_relationship_event,
    record_stat_event,
    add_equipment,
    remove_equipment,
    remember_event,
]
