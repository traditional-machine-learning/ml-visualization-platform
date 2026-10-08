package com.mlplatform.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

public class DatasetDTO {
    private Long id;
    private String name;
    private String description;
    private String category;
    private Integer featureCount;
    private Integer sampleCount;
    private List<FeatureInfo> features;
    private List<DataPoint> dataPoints;

    /** 数据集声明的全部维度元数据（仅详情接口返回，列表接口为 null 不序列化） */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<FeatureInfo> dimensions;

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

        /** 该行各维度的原始值（按特征名索引，仅详情接口返回，列表接口为 null 不序列化） */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private Map<String, Double> values;

        public Map<String, Double> getValues() { return values; }
        public void setValues(Map<String, Double> values) { this.values = values; }
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
    public List<FeatureInfo> getDimensions() { return dimensions; }
    public void setDimensions(List<FeatureInfo> dimensions) { this.dimensions = dimensions; }
}