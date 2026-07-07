package com.mlplatform.model;

import java.time.LocalDateTime;

public class TrainingSession {
    private Long id;
    private Long algorithmId;
    private Long datasetId;
    private String parameters; // JSON string: actual parameter values used
    private String status; // "idle", "training", "paused", "completed"
    private Integer currentStep;
    private Integer totalSteps;
    private String metrics; // JSON string: training metrics
    private String modelData; // JSON string: trained model state
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAlgorithmId() { return algorithmId; }
    public void setAlgorithmId(Long algorithmId) { this.algorithmId = algorithmId; }
    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long datasetId) { this.datasetId = datasetId; }
    public String getParameters() { return parameters; }
    public void setParameters(String parameters) { this.parameters = parameters; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getCurrentStep() { return currentStep; }
    public void setCurrentStep(Integer currentStep) { this.currentStep = currentStep; }
    public Integer getTotalSteps() { return totalSteps; }
    public void setTotalSteps(Integer totalSteps) { this.totalSteps = totalSteps; }
    public String getMetrics() { return metrics; }
    public void setMetrics(String metrics) { this.metrics = metrics; }
    public String getModelData() { return modelData; }
    public void setModelData(String modelData) { this.modelData = modelData; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}