# Narrative Engine

File-based character engine. Each character is a folder of JSON files under `Characters/`. `character.json` is the hub; the backend combines, creates, and updates those files.

## Architecture

**Version:** my architecture diagram

![my architecture diagram](architecture%20diagram.png)

## APIs

Run the backend from `backend/narrative-engine` with `.\mvnw quarkus:dev`. Base URL: `http://localhost:8080`.

| Method | URL | Purpose |
|---|---|---|
| GET | `/characters/{name}` | Combined character JSON |
| POST | `/create_new_character/{name}/{class}` | Create folder and JSON files from mage or melee defaults |
| PUT | `/update_character/{name}` | Merge fields into existing JSON |

Authoring notes for the JSON files are in [NOTES.md](NOTES.md).
