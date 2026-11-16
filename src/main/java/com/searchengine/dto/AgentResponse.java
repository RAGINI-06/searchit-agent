package com.searchengine.dto;

import tools.jackson.databind.JsonNode;
import java.util.List;

public record AgentResponse(
        String status,        // "success" | "partial" | "failed"
        String summary,       // readable summary, or the error explanation
        JsonNode data,        // the crawl result JSON (null if the crawl failed)
        boolean wantsJson,    // true if the user asked for JSON
        List<String> steps,   // execution log shown in the chat
        int exitCode,         // CLI exit code (0 ok, 2 invalid, 3 nothing fetched, 4 partial, -1 timeout)
        long durationMs,      // how long the CLI ran
        String stderr         // CLI error output, trimmed
) {}