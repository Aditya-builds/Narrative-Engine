STAT_KEYS = (
    "vigor",
    "mind",
    "endurance",
    "strength",
    "dexterity",
    "intelligence",
    "faith",
    "arcane",
)

EQUIPMENT_SLOTS = ("weapons", "armor", "accessories")
ENTITY_TYPES = ("character", "persona")
SEVERITIES = ("minor", "major")
RANKS = ("E", "D", "C", "B", "A", "S")

RELATIONSHIP_EVENTS = {
    ("compliment", "minor"): 2,
    ("compliment", "major"): 5,
    ("kindness", "minor"): 2,
    ("kindness", "major"): 6,
    ("defended_guild", "minor"): 4,
    ("defended_guild", "major"): 7,
    ("fought_together", "minor"): 3,
    ("fought_together", "major"): 6,
    ("saved_life", "minor"): 6,
    ("saved_life", "major"): 8,
    ("promise", "minor"): 3,
    ("promise", "major"): 5,
    ("insult", "minor"): -2,
    ("insult", "major"): -5,
    ("mockery", "minor"): -2,
    ("mockery", "major"): -6,
    ("guild_insult", "minor"): -4,
    ("guild_insult", "major"): -7,
    ("betrayal", "minor"): -6,
    ("betrayal", "major"): -8,
    ("attack", "minor"): -3,
    ("attack", "major"): -6,
}

STAT_EVENTS = {
    ("injury", "minor"): -3,
    ("injury", "major"): -7,
    ("exhaustion", "minor"): -2,
    ("exhaustion", "major"): -5,
    ("recover", "minor"): 2,
    ("recover", "major"): 5,
    ("training", "minor"): 1,
    ("training", "major"): 3,
}
