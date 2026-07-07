package com.mlplatform.dto;

public class AiAssistantRequest {
    private String question;
    private String algorithmName;
    private String algorithmDisplayName;
    private String algorithmDescription;
    private String datasetName;
    private String datasetDescription;
    private String trainingStatus;
    private Integer currentStep;
    private Integer totalSteps;
    private Double loss;
    private Double accuracy;
    private String parametersJson;

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public String getAlgorithmName() { return algorithmName; }
    public void setAlgorithmName(String algorithmName) { this.algorithmName = algorithmName; }

    public String getAlgorithmDisplayName() { return algorithmDisplayName; }
    public void setAlgorithmDisplayName(String algorithmDisplayName) { this.algorithmDisplayName = algorithmDisplayName; }

    public String getAlgorithmDescription() { return algorithmDescription; }
    public void setAlgorithmDescription(String algorithmDescription) { this.algorithmDescription = algorithmDescription; }

    public String getDatasetName() { return datasetName; }
    public void setDatasetName(String datasetName) { this.datasetName = datasetName; }

    public String getDatasetDescription() { return datasetDescription; }
    public void setDatasetDescription(String datasetDescription) { this.datasetDescription = datasetDescription; }

    public String getTrainingStatus() { return trainingStatus; }
    public void setTrainingStatus(String trainingStatus) { this.trainingStatus = trainingStatus; }

    public Integer getCurrentStep() { return currentStep; }
    public void setCurrentStep(Integer currentStep) { this.currentStep = currentStep; }

    public Integer getTotalSteps() { return totalSteps; }
    public void setTotalSteps(Integer totalSteps) { this.totalSteps = totalSteps; }

    public Double getLoss() { return loss; }
    public void setLoss(Double loss) { this.loss = loss; }

    public Double getAccuracy() { return accuracy; }
    public void setAccuracy(Double accuracy) { this.accuracy = accuracy; }

    public String getParametersJson() { return parametersJson; }
    public void setParametersJson(String parametersJson) { this.parametersJson = parametersJson; }
}