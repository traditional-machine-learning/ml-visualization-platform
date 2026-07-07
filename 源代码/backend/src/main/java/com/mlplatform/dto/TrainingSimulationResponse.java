package com.mlplatform.dto;

import java.util.List;

public class TrainingSimulationResponse {
    private Long sessionId;
    private Long algorithmId;
    private Long datasetId;
    private String status;
    private Integer currentStep;
    private Integer totalSteps;
    private Double loss;
    private Double accuracy;
    private String scoreLabel;
    private Metrics metrics;
    private ModelData modelData;

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public Long getAlgorithmId() { return algorithmId; }
    public void setAlgorithmId(Long algorithmId) { this.algorithmId = algorithmId; }
    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long datasetId) { this.datasetId = datasetId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getCurrentStep() { return currentStep; }
    public void setCurrentStep(Integer currentStep) { this.currentStep = currentStep; }
    public Integer getTotalSteps() { return totalSteps; }
    public void setTotalSteps(Integer totalSteps) { this.totalSteps = totalSteps; }
    public Double getLoss() { return loss; }
    public void setLoss(Double loss) { this.loss = loss; }
    public Double getAccuracy() { return accuracy; }
    public void setAccuracy(Double accuracy) { this.accuracy = accuracy; }
    public String getScoreLabel() { return scoreLabel; }
    public void setScoreLabel(String scoreLabel) { this.scoreLabel = scoreLabel; }
    public Metrics getMetrics() { return metrics; }
    public void setMetrics(Metrics metrics) { this.metrics = metrics; }
    public ModelData getModelData() { return modelData; }
    public void setModelData(ModelData modelData) { this.modelData = modelData; }

    public static class Metrics {
        private List<MetricPoint> points;
        private String lossLabel;
        private String scoreLabel;

        public List<MetricPoint> getPoints() { return points; }
        public void setPoints(List<MetricPoint> points) { this.points = points; }
        public String getLossLabel() { return lossLabel; }
        public void setLossLabel(String lossLabel) { this.lossLabel = lossLabel; }
        public String getScoreLabel() { return scoreLabel; }
        public void setScoreLabel(String scoreLabel) { this.scoreLabel = scoreLabel; }
    }

    public static class MetricPoint {
        private Integer step;
        private Double loss;
        private Double score;

        public Integer getStep() { return step; }
        public void setStep(Integer step) { this.step = step; }
        public Double getLoss() { return loss; }
        public void setLoss(Double loss) { this.loss = loss; }
        public Double getScore() { return score; }
        public void setScore(Double score) { this.score = score; }
    }

    public static class ModelData {
        private List<List<Double>> decisionBoundary;
        private List<List<Double>> regressionLine;
        private List<Centroid> centroids;
        private List<Double> weights;
        private Double bias;
        private List<Integer> assignments;
        private String splitFeature;
        private Double splitThreshold;
        private Double splitImpurity;
        private List<List<Double>> pcaComponents;
        private List<List<Double>> pcaTransformed;
        private List<FeatureImportance> featureImportances;

        public List<List<Double>> getDecisionBoundary() { return decisionBoundary; }
        public void setDecisionBoundary(List<List<Double>> decisionBoundary) { this.decisionBoundary = decisionBoundary; }
        public List<List<Double>> getRegressionLine() { return regressionLine; }
        public void setRegressionLine(List<List<Double>> regressionLine) { this.regressionLine = regressionLine; }
        public List<Centroid> getCentroids() { return centroids; }
        public void setCentroids(List<Centroid> centroids) { this.centroids = centroids; }
        public List<Double> getWeights() { return weights; }
        public void setWeights(List<Double> weights) { this.weights = weights; }
        public Double getBias() { return bias; }
        public void setBias(Double bias) { this.bias = bias; }
        public List<Integer> getAssignments() { return assignments; }
        public void setAssignments(List<Integer> assignments) { this.assignments = assignments; }
        public String getSplitFeature() { return splitFeature; }
        public void setSplitFeature(String splitFeature) { this.splitFeature = splitFeature; }
        public Double getSplitThreshold() { return splitThreshold; }
        public void setSplitThreshold(Double splitThreshold) { this.splitThreshold = splitThreshold; }
        public Double getSplitImpurity() { return splitImpurity; }
        public void setSplitImpurity(Double splitImpurity) { this.splitImpurity = splitImpurity; }
        public List<List<Double>> getPcaComponents() { return pcaComponents; }
        public void setPcaComponents(List<List<Double>> pcaComponents) { this.pcaComponents = pcaComponents; }
        public List<List<Double>> getPcaTransformed() { return pcaTransformed; }
        public void setPcaTransformed(List<List<Double>> pcaTransformed) { this.pcaTransformed = pcaTransformed; }
        public List<FeatureImportance> getFeatureImportances() { return featureImportances; }
        public void setFeatureImportances(List<FeatureImportance> featureImportances) { this.featureImportances = featureImportances; }
    }

    public static class Centroid {
        private Double x;
        private Double y;
        private Integer clusterId;

        public Double getX() { return x; }
        public void setX(Double x) { this.x = x; }
        public Double getY() { return y; }
        public void setY(Double y) { this.y = y; }
        public Integer getClusterId() { return clusterId; }
        public void setClusterId(Integer clusterId) { this.clusterId = clusterId; }
    }

    public static class FeatureImportance {
        private String feature;
        private Double importance;

        public String getFeature() { return feature; }
        public void setFeature(String feature) { this.feature = feature; }
        public Double getImportance() { return importance; }
        public void setImportance(Double importance) { this.importance = importance; }
    }
}
