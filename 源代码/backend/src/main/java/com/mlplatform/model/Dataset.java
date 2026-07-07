package com.mlplatform.model;

import java.time.LocalDateTime;

public class Dataset {
    private Long id;
    private String name;
    private String description;
    private String category; // "supervised", "unsupervised", "reinforcement"
    private String fileName;
    private Integer featureCount;
    private Integer sampleCount;
    private String features; // JSON string: feature names and types
    private String dataContent; // JSON string: the actual data points
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public Integer getFeatureCount() { return featureCount; }
    public void setFeatureCount(Integer featureCount) { this.featureCount = featureCount; }
    public Integer getSampleCount() { return sampleCount; }
    public void setSampleCount(Integer sampleCount) { this.sampleCount = sampleCount; }
    public String getFeatures() { return features; }
    public void setFeatures(String features) { this.features = features; }
    public String getDataContent() { return dataContent; }
    public void setDataContent(String dataContent) { this.dataContent = dataContent; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}