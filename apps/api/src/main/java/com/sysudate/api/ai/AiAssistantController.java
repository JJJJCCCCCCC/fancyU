package com.sysudate.api.ai;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiAssistantController {
    private final AiAssistantClient assistant;

    public AiAssistantController(AiAssistantClient assistant) {
        this.assistant = assistant;
    }

    @PostMapping("/llm/chat")
    public Map<String, Object> chat(@RequestBody ChatRequest request) {
        return Map.of(
            "code", 200,
            "data", assistant.chat(request.messages()));
    }

    public record ChatRequest(List<Map<String, Object>> messages) {
    }
}
