# Narrative Engine notes

These notes are for the author. They are not character data. Do not put comments inside the `.json` files.

## Architecture

**Version:** my architecture diagram

![my architecture diagram](architecture%20diagram.png)

Each character lives in `Characters/{Name}/`. Names are unique. `character.json` is the hub and points at domain files in the same folder. The backend combines those files on GET, creates a new character from mage or melee class defaults on POST, and merges field updates on PUT.

## Creating a character

`POST /create_new_character/{name}/{class}` writes a new folder. `{class}` must be `mage` or `melee` (`melle` is accepted as melee). The name must be unique.

The starter files are class templates, not copies of Aurora or Laxus:

- `character.json` gets `class`, starting rank `E`, `location` `"guildhall"`, and a class description
- `stats.json` and `equipment.json` use mage or melee defaults
- `abilities.json` sets `classes` to the chosen class and empty rank lists under `arcane` (mage) or `martial` (melee)
- appearance, relationships, and visual identity start empty with the same keys

Do not copy Aurora's ice specialty, appearance, or relationships into a new character.

## character.json

- `name` is unique and matches the folder name. APIs look characters up by this name.
- `location` is a string. New characters default to `"guildhall"`.
- `class` is `mage` or `melee`. New characters get this from the create API.
- `description` is a short class starter blurb. It can be updated later.
- `files` maps a domain name to a child filename in the same folder.
- `visualIdentity` is the map key; the file on disk is `visual-identity.json`.

## abilities.json

- Each rank key (`E`, `D`, `C`, `B`, `A`, `S`) holds an array of spell name strings.
- Offensive and defensive lists are separate.
- A character should not use spells above their rank. That rule belongs in the application later, not in this file.

## relationships.json

- Keys are other character names.
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
