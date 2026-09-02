module.exports = {
  "/characters": { target: "http://localhost:8080", secure: false },
  "/personas": { target: "http://localhost:8080", secure: false },
  "/create_new_character": { target: "http://localhost:8080", secure: false },
  "/create_new_persona": { target: "http://localhost:8080", secure: false },
  "/update_character": { target: "http://localhost:8080", secure: false },
  "/update_persona": { target: "http://localhost:8080", secure: false },
  "/chat": {
    target: "http://localhost:8000",
    secure: false,
    bypass: function (req) {
      const path = (req.url || "").split("?")[0];
      if (path === "/chats" || path.startsWith("/chats/")) {
        return req.url;
      }
    }
  },
  "/conversations": { target: "http://localhost:8000", secure: false },
  "/llm-config": { target: "http://localhost:8000", secure: false }
};
