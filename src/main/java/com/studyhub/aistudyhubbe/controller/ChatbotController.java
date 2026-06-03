package com.studyhub.aistudyhubbe.controller;

import com.studyhub.aistudyhubbe.dto.ApiResponse;
import com.studyhub.aistudyhubbe.dto.ChatMessageResponse;
import com.studyhub.aistudyhubbe.dto.ChatRequest;
import com.studyhub.aistudyhubbe.dto.PageResponse;
import com.studyhub.aistudyhubbe.exception.ApiException;
import com.studyhub.aistudyhubbe.security.UserPrincipal;
import com.studyhub.aistudyhubbe.service.ChatbotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chatbot")
@Tag(name = "Chatbot", description = "Study assistant chat endpoints")
public class ChatbotController {

    private final ChatbotService chatbotService;

    public ChatbotController(ChatbotService chatbotService) {
        this.chatbotService = chatbotService;
    }

    @Operation(summary = "Send a message to the study assistant")
    @PostMapping("/messages")
    public ApiResponse<ChatMessageResponse> sendMessage(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChatRequest request) {
        return ApiResponse.ok(
                "Chat response generated",
                chatbotService.sendMessage(requireUserId(principal), request));
    }

    @Operation(summary = "List current user's chat history")
    @GetMapping("/history")
    public ApiResponse<PageResponse<ChatMessageResponse>> listHistory(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(chatbotService.listHistory(requireUserId(principal), page, size));
    }

    @Operation(summary = "Clear current user's chat history")
    @DeleteMapping("/history")
    public ApiResponse<Void> clearHistory(@AuthenticationPrincipal UserPrincipal principal) {
        chatbotService.clearHistory(requireUserId(principal));
        return ApiResponse.ok("Chat history cleared", null);
    }

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return principal.getId();
    }
}
