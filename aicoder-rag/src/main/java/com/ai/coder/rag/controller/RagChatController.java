package com.ai.coder.rag.controller;

import com.ai.coder.rag.dto.RagChatRequest;
import com.ai.coder.rag.service.RagChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagChatController {

    private final RagChatService ragChatService;

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@RequestBody RagChatRequest request) {
        return ragChatService.ragChatStream(request)
                .map(content -> ServerSentEvent.<String>builder()
                        .data(content)
                        .build());
    }
}
