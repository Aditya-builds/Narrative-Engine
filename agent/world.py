import os

import httpx

from constants import RANKS, STAT_KEYS


class WorldApiError(RuntimeError):
    pass


def get_entity(kind: str, name: str) -> dict:
    path = f"/{_collection(kind)}/{name}"
    last_error: Exception | None = None
    for attempt in range(2):
        try:
            with _client() as client:
                response = client.get(path)
            break
        except httpx.RequestError as exc:
            last_error = exc
            if attempt == 0:
                continue
            raise WorldApiError(f"Could not reach Quarkus for GET {path}") from exc
    else:
        raise WorldApiError(f"Could not reach Quarkus for GET {path}") from last_error
    if response.status_code == 404:
        raise WorldApiError(f"{kind} '{name}' was not found")
    if response.status_code >= 400:
        raise WorldApiError(f"Quarkus GET {path} failed: {response.status_code} {response.text}")
    return response.json()


def update_entity(kind: str, name: str, body: dict) -> None:
    path = f"/{_update_path(kind)}/{name}"
    with _client() as client:
        response = client.put(path, json=body)
    if response.status_code == 404:
        raise WorldApiError(f"{kind} '{name}' was not found")
    if response.status_code >= 400:
        raise WorldApiError(f"Quarkus PUT {path} failed: {response.status_code} {response.text}")


def domain_file(entity: dict, domain: str) -> dict:
    files = entity.get("files") or {}
    if not isinstance(files, dict):
        return {}
    direct = files.get(domain)
    if isinstance(direct, dict):
        return direct
    nested = files.get(f"{domain}.json")
    if isinstance(nested, dict):
        return nested
    return {}


def load_world(character_id: str, persona_id: str) -> tuple[dict, dict, list[str]]:
    notes: list[str] = []
    character = runtime_context(_safe_load("character", character_id, notes))
    persona = runtime_context(_safe_load("persona", persona_id, notes))
    return character, persona, notes


def runtime_context(entity: dict) -> dict:
    if not entity or entity.get("error"):
        return entity
    personality = domain_file(entity, "personality")
    relationships = domain_file(entity, "relationships")
    stats = domain_file(entity, "stats")
    equipment = domain_file(entity, "equipment")
    appearance = domain_file(entity, "appearance")
    abilities = domain_file(entity, "abilities")
    attributes = stats.get("attributes") if isinstance(stats.get("attributes"), dict) else {}
    rank = str(entity.get("rank") or "E")
    return {
        "identity": {
            "name": entity.get("name"),
            "class": entity.get("class"),
            "rank": rank,
            "gender": entity.get("gender"),
            "age": entity.get("age"),
            "description": entity.get("description"),
        },
        "personality": personality,
        "current_state": {
            "location": entity.get("location"),
            "attributes": {key: attributes.get(key) for key in STAT_KEYS if key in attributes},
            "strengths": stats.get("strengths"),
            "weaknesses": stats.get("weaknesses"),
        },
        "abilities": _abilities_for_rank(abilities, rank),
        "relationships": relationships.get("relationships", relationships),
        "appearance": appearance,
        "equipment": {
            "weapons": equipment.get("weapons"),
            "armor": equipment.get("armor"),
            "accessories": equipment.get("accessories"),
            "clothing": equipment.get("clothing"),
        },
    }


def select_relevant_context(character: dict, persona: dict, user_message: str) -> str:
    layers = _needed_layers(user_message)
    return (
        "CHARACTER (you are speaking as this person):\n"
        f"{_pick_layers(character, layers)}\n\n"
        "PERSONA (the user is speaking as this person):\n"
        f"{_pick_layers(persona, layers)}"
    )


def compact_context(character: dict, persona: dict) -> str:
    return select_relevant_context(character, persona, "")


def _needed_layers(user_message: str) -> list[str]:
    text = (user_message or "").lower()
    layers = ["identity", "personality"]
    combat = any(word in text for word in ("fight", "attack", "magic", "spell", "beat", "power", "ability"))
    look = any(word in text for word in ("look", "appear", "hair", "eyes", "wear", "clothes", "face"))
    gear = any(word in text for word in ("weapon", "armor", "gear", "equip", "give", "take"))
    bond = any(word in text for word in ("guild", "trust", "hate", "like", "love", "insult", "pathetic", "promise", "save"))
    body = any(word in text for word in ("hurt", "wound", "injur", "tired", "exhaust", "heal", "stat"))
    if combat:
        layers.extend(["current_state", "abilities"])
    if look:
        layers.extend(["appearance", "equipment"])
    if gear:
        layers.append("equipment")
    if bond:
        layers.append("relationships")
    if body:
        layers.append("current_state")
    if layers == ["identity", "personality"]:
        layers.extend(["relationships", "current_state"])
    return list(dict.fromkeys(layers))


def _pick_layers(entity: dict, layers: list[str]) -> dict:
    if not entity:
        return {}
    if entity.get("error"):
        return entity
    if "identity" not in entity:
        return entity
    picked = {layer: entity.get(layer) for layer in layers if layer in entity}
    return _drop_empty(picked)


def _drop_empty(value):
    if isinstance(value, dict):
        cleaned = {key: _drop_empty(item) for key, item in value.items()}
        return {key: item for key, item in cleaned.items() if item not in (None, "", {}, [])}
    if isinstance(value, list):
        cleaned = [_drop_empty(item) for item in value]
        return [item for item in cleaned if item not in (None, "", {}, [])]
    return value


def _abilities_for_rank(abilities: dict, rank: str) -> dict:
    specialties = abilities.get("specialties") if isinstance(abilities.get("specialties"), dict) else {}
    allowed = set(RANKS[: RANKS.index(rank) + 1] if rank in RANKS else ("E",))
    trimmed = {}
    for name, tree in specialties.items():
        if not isinstance(tree, dict):
            continue
        trimmed[name] = {
            "offensive": _rank_spells(tree.get("offensive"), allowed),
            "defensive": _rank_spells(tree.get("defensive"), allowed),
        }
    return {"classes": abilities.get("classes"), "usable_now": trimmed}


def _rank_spells(bucket: object, allowed: set[str]) -> list[str]:
    if not isinstance(bucket, dict):
        return []
    spells: list[str] = []
    for rank, names in bucket.items():
        if rank in allowed and isinstance(names, list):
            spells.extend(str(name) for name in names)
    return spells


def _client() -> httpx.Client:
    from observability import current_request_id

    base = os.getenv("QUARKUS_BASE_URL", "http://localhost:8080").rstrip("/")
    headers = {}
    request_id = current_request_id()
    if request_id:
        headers["X-Request-ID"] = request_id
    return httpx.Client(base_url=base, timeout=15.0, headers=headers)


def _safe_load(kind: str, name: str, notes: list[str]) -> dict:
    if not name:
        notes.append(f"No {kind} name was provided.")
        return {}
    try:
        return get_entity(kind, name)
    except WorldApiError as exc:
        notes.append(str(exc))
        return {"name": name, "error": str(exc)}
    except Exception as exc:
        notes.append(f"Could not reach Quarkus for {kind} '{name}': {exc}")
        return {"name": name, "error": str(exc)}


def _collection(kind: str) -> str:
    if kind == "character":
        return "characters"
    if kind == "persona":
        return "personas"
    raise WorldApiError("entity_type must be 'character' or 'persona'")


def _update_path(kind: str) -> str:
    if kind == "character":
        return "update_character"
    if kind == "persona":
        return "update_persona"
    raise WorldApiError("entity_type must be 'character' or 'persona'")
