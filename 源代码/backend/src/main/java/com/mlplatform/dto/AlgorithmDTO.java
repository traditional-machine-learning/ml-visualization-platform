package com.mlplatform.dto;

import java.util.List;

public class AlgorithmDTO {
    private Long id;
    private String name;
    private String displayName;
    private String description;
    private String category;
    private String type;
    private List<ParameterDef> parameters;

    public static class ParameterDef {
        private String name;
        private String displayName;
        private String type; // "number", "select"
        private Object defaultObj;
        private Double min;
        private Double max;
        private Double step;
        private List<String> options;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public Object getDefault() { return defaultObj; }
        public void setDefault(Object defaultObj) { this.defaultObj = defaultObj; }
        public Double getMin() { return min; }
        public void setMin(Double min) { this.min = min; }
        public Double getMax() { return max; }
        public void setMax(Double max) { this.max = max; }
        public Double getStep() { return step; }
        public void setStep(Double step) { this.step = step; }
        public List<String> getOptions() { return options; }
        public void setOptions(List<String> options) { this.options = options; }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public List<ParameterDef> getParameters() { return parameters; }
    public void setParameters(List<ParameterDef> parameters) { this.parameters = parameters; }
}