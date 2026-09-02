from memory.threads import delete_chat_thread, list_chat_threads, load_chat_thread, save_chat_thread


def test_chat_thread_survives_reload(tmp_path, monkeypatch):
    chats = tmp_path / "chats"
    monkeypatch.setattr("memory.threads.CHATS_DIR", chats)

    saved = save_chat_thread(
        "Mirajane",
        {
            "conversation_id": "conv-1",
            "persona_name": "Laxus",
            "reply_length": "short",
            "messages": [
                {
                    "speaker": "character",
                    "name": "Mirajane",
                    "text": "Hello.",
                    "at": "2026-09-02T12:00:00+00:00",
                },
                {
                    "speaker": "persona",
                    "name": "Laxus",
                    "text": "Hey.",
                    "at": "2026-09-02T12:00:01+00:00",
                },
            ],
        },
    )
    assert (chats / "Mirajane.json").exists()
    assert saved["conversation_id"] == "conv-1"

    loaded = load_chat_thread("Mirajane")
    assert loaded is not None
    assert loaded["persona_name"] == "Laxus"
    assert loaded["messages"][1]["text"] == "Hey."

    listed = list_chat_threads()
    assert len(listed) == 1
    assert listed[0]["character"] == "Mirajane"
    assert listed[0]["preview"] == "Hey."

    delete_chat_thread("Mirajane")
    assert load_chat_thread("Mirajane") is None
    assert list_chat_threads() == []


def test_character_names_with_spaces_do_not_collide(tmp_path, monkeypatch):
    chats = tmp_path / "chats"
    monkeypatch.setattr("memory.threads.CHATS_DIR", chats)

    save_chat_thread(
        "Mary Jane",
        {
            "conversation_id": "a",
            "persona_name": "Laxus",
            "messages": [
                {"speaker": "persona", "name": "Laxus", "text": "one", "at": "2026-09-02T12:00:00+00:00"}
            ],
        },
    )
    save_chat_thread(
        "MaryJane",
        {
            "conversation_id": "b",
            "persona_name": "Laxus",
            "messages": [
                {"speaker": "persona", "name": "Laxus", "text": "two", "at": "2026-09-02T12:00:00+00:00"}
            ],
        },
    )
    one = load_chat_thread("Mary Jane")
    two = load_chat_thread("MaryJane")
    assert one is not None and two is not None
    assert one["messages"][0]["text"] == "one"
    assert two["messages"][0]["text"] == "two"
    assert len(list_chat_threads()) == 2
