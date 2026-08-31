# Narrative Engine notes

These notes are for the author. They are not character data. Do not put comments inside the `.json` files.

## Architecture

**Version:** my architecture diagram

![my architecture diagram](architecture%20diagram.png)

Each character lives in `Characters/{Name}/`. `character.json` is the hub and points at domain files in the same folder. The backend combines those files on GET, creates an empty copy of this structure on POST, and merges field updates on PUT.

## character.json

- `id` is a stable identifier. A unique-id generator will be added later. Until then, set it by hand (example: `"aurora"`).
- `files` maps a domain name to a child filename in the same folder.
- `visualIdentity` is the map key; the file on disk is `visual-identity.json`.

## abilities.json

- Each rank key (`E`, `D`, `C`, `B`, `A`, `S`) holds an array of spell name strings.
- Offensive and defensive lists are separate.
- A character should not use spells above their rank. That rule belongs in the application later, not in this file.

## relationships.json

- Keys are other character ids, not display names.
- Values are affinity numbers. Treat these as changeable state, not fixed identity.

## appearance.json

- `0` is a real measurement. If a value is unknown, omit the field instead of using `0`.
- `"..."` is a placeholder, not appearance data.

## equipment.json

- Arrays are items the character has. Clothing strings are named outfits.
- This file does not yet say which outfit is currently worn.

## visual-identity.json

- Image paths are relative to this character until an assets root is decided.
- These references will be used to generate consistent images for the character.
