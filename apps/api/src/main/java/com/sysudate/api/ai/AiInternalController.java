package com.sysudate.api.ai;

import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AiInternalController {
    private final ObjectProvider<AiQueryRepository> queries;

    public AiInternalController(ObjectProvider<AiQueryRepository> queries) {
        this.queries = queries;
    }

    @PostMapping("/internal/ai/query")
    public Map<String, Object> query(@RequestBody QueryRequest request) {
        if (request.intent() == null || request.parameters() == null) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid query request");
        }
        AiQueryRepository repository = queries.getIfAvailable();
        if (repository == null) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Query service unavailable");
        }
        return repository.execute(request.intent(), request.parameters());
    }

    public record QueryRequest(
        String intent,
        Map<String, Object> parameters
    ) {
    }
}
