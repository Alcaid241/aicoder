package com.ai.coder.chat.controller;

import com.ai.coder.chat.dto.AgentRequest;
import com.ai.coder.chat.service.AgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @PostMapping(value = "/agent/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> agentStream(@RequestBody AgentRequest request,
                                                      @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId) {
        return agentService.agentStream(request, userId);
    }
}
