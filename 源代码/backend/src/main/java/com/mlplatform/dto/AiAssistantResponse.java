package com.mlplatform.dto;

public class AiAssistantResponse {
    private String answer;
    private boolean success;
    private String errorMessage;

    public static AiAssistantResponse success(String answer) {
        AiAssistantResponse response = new AiAssistantResponse();
        response.setSuccess(true);
        response.setAnswer(answer);
        return response;
    }

    public static AiAssistantResponse error(String errorMessage) {
        AiAssistantResponse response = new AiAssistantResponse();
        response.setSuccess(false);
        response.setErrorMessage(errorMessage);
        return response;
    }

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}