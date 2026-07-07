package com.mlplatform.controller;

import com.mlplatform.dto.ApiResponse;
import com.mlplatform.dto.AiAssistantRequest;
import com.mlplatform.dto.AiAssistantResponse;
import com.mlplatform.service.AiAssistantService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai-assistant")
public class AiAssistantController {

    private final AiAssistantService aiAssistantService;

    public AiAssistantController(AiAssistantService aiAssistantService) {
        this.aiAssistantService = aiAssistantService;
    }

    @PostMapping("/ask")
    public ApiResponse<AiAssistantResponse> askQuestion(@RequestBody AiAssistantRequest request) {
        AiAssistantResponse response = aiAssistantService.getAnswer(request);
        if (response.isSuccess()) {
            return ApiResponse.success(response);
        }
        return ApiResponse.error(response.getErrorMessage());
    }
}