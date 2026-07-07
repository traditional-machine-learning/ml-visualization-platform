package com.mlplatform.dto;

import java.util.List;

public class DatasetDTO {
    private Long id;
    private String name;
    private String description;
    private String category;
    private Integer featureCount;
    private Integer sampleCount;
    private List<FeatureInfo> features;
    private List<DataPoint> dataPoints;

    public static class FeatureInfo {
        private String name;
        private String displayName;
        private String type;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }

    public static class DataPoint {
        private Double x;
        private Double y;
        private String label;
        private Integer clusterId;

        public Double getX() { return x; }
        public void setX(Double x) { this.x = x; }
        public Double getY() { return y; }
        public void setY(Double y) { this.y = y; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public Integer getClusterId() { return clusterId; }
        public void setClusterId(Integer clusterId) { this.clusterId = clusterId; }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Integer getFeatureCount() { return featureCount; }
    public void setFeatureCount(Integer featureCount) { this.featureCount = featureCount; }
    public Integer getSampleCount() { return sampleCount; }
    public void setSampleCount(Integer sampleCount) { this.sampleCount = sampleCount; }
    public List<FeatureInfo> getFeatures() { return features; }
    public void setFeatures(List<FeatureInfo> features) { this.features = features; }
    public List<DataPoint> getDataPoints() { return dataPoints; }
    public void setDataPoints(List<DataPoint> dataPoints) { this.dataPoints = dataPoints; }
}