package org.example.back.controller;

import org.example.back.service.AiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private AiService aiService;

    @PostMapping(produces = "application/json")
    public String chat(@RequestBody Map<String, String> request) {
        String message = request.getOrDefault("message", "");
        String profile = request.getOrDefault("profile", "");
        String history = request.getOrDefault("history", "");

        if (message.isBlank()) {
            return "{\"validare_emotionala\": \"Te rog introdu un mesaj.\"}";
        }
        return aiService.getChatResponse(message, profile, history);
    }
}
