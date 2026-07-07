package com.mlplatform.service;

import com.mlplatform.dto.AiAssistantRequest;
import com.mlplatform.dto.AiAssistantResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
public class AiAssistantService {

    private final WebClient webClient;

    @Value("${llm.model:gpt-3.5-turbo}")
    private String model;

    @Value("${llm.max-tokens:1000}")
    private int maxTokens;

    public AiAssistantService(
            @Value("${llm.endpoint:https://api.openai.com/v1}") String endpoint,
            @Value("${llm.api-key:}") String apiKey) {
        this.webClient = WebClient.builder()
                .baseUrl(endpoint)
                .defaultHeader("Content-Type", "application/json")
                .build();
        // Store API key for use in requests
        this.apiKey = apiKey;
    }

    private String apiKey;

    public AiAssistantResponse getAnswer(AiAssistantRequest request) {
        if (apiKey == null || apiKey.isEmpty()) {
            return AiAssistantResponse.error("AI API key not configured. Please set LLM_API_KEY environment variable.");
        }

        String systemPrompt = buildSystemPrompt(request);
        String userPrompt = buildUserPrompt(request);

        try {
            Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)
                ),
                "max_tokens", maxTokens
            );

            String response = webClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(this::extractContent)
                .block();

            return AiAssistantResponse.success(response);
        } catch (Exception e) {
            return AiAssistantResponse.error("Failed to get AI response: " + e.getMessage());
        }
    }

    private String buildSystemPrompt(AiAssistantRequest request) {
        return "You are an AI assistant helping users understand machine learning experiments. " +
               "Answer questions about algorithms, datasets, training progress, and ML concepts. " +
               "Be concise and helpful. Respond in the same language as the user's question.";
    }

    private String buildUserPrompt(AiAssistantRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("Context:\n");
        if (request.getAlgorithmName() != null) {
            sb.append("- Algorithm: ").append(request.getAlgorithmDisplayName())
              .append(" (").append(request.getAlgorithmDescription() != null ? request.getAlgorithmDescription() : "").append(")\n");
        }
        if (request.getDatasetName() != null) {
            sb.append("- Dataset: ").append(request.getDatasetName())
              .append(" (").append(request.getDatasetDescription() != null ? request.getDatasetDescription() : "").append(")\n");
        }
        if (request.getTrainingStatus() != null) {
            sb.append("- Training Status: ").append(request.getTrainingStatus());
            if (request.getCurrentStep() != null && request.getTotalSteps() != null) {
                sb.append(" (Step ").append(request.getCurrentStep())
                  .append("/").append(request.getTotalSteps()).append(")");
            }
            sb.append("\n");
        }
        if (request.getLoss() != null || request.getAccuracy() != null) {
            sb.append("- Current Metrics: ");
            if (request.getLoss() != null) {
                sb.append("Loss=").append(String.format("%.3f", request.getLoss()));
            }
            if (request.getAccuracy() != null) {
                sb.append(", Accuracy=").append(String.format("%.2f", request.getAccuracy() * 100)).append("%");
            }
            sb.append("\n");
        }
        if (request.getParametersJson() != null && !request.getParametersJson().isEmpty()) {
            sb.append("- Parameters: ").append(request.getParametersJson()).append("\n");
        }
        sb.append("\nQuestion: ").append(request.getQuestion());
        return sb.toString();
    }

    private String extractContent(Map response) {
        List choices = (List) response.get("choices");
        if (choices != null && !choices.isEmpty()) {
            Map choice = (Map) choices.get(0);
            Map message = (Map) choice.get("message");
            if (message != null) {
                return (String) message.get("content");
            }
        }
        return "No response received from AI.";
    }
}