package com.mlplatform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mlplatform.dto.DatasetDTO;
import com.mlplatform.model.Dataset;
import com.mlplatform.repository.DatasetRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DatasetService {

    private final DatasetRepository datasetRepository;
    private final ObjectMapper objectMapper;

    public DatasetService(DatasetRepository datasetRepository, ObjectMapper objectMapper) {
        this.datasetRepository = datasetRepository;
        this.objectMapper = objectMapper;
    }

    public List<DatasetDTO> getAllDatasets() {
        return datasetRepository.findAll().stream()
                .map(dataset -> convertToDTO(dataset, false))
                .collect(Collectors.toList());
    }

    /** 详情接口：额外返回全维度元数据与每行的原始维度值，供前端自由选择坐标轴 */
    public DatasetDTO getDatasetById(Long id) {
        Dataset dataset = datasetRepository.findById(id);
        if (dataset == null) {
            throw new IllegalArgumentException("Dataset not found with id: " + id);
        }
        return convertToDTO(dataset, true);
    }

    public List<DatasetDTO> getDatasetsByCategory(String category) {
        return datasetRepository.findByCategory(category).stream()
                .map(dataset -> convertToDTO(dataset, false))
                .collect(Collectors.toList());
    }

    public DatasetDTO createDataset(Dataset dataset) {
        datasetRepository.insert(dataset);
        Dataset created = datasetRepository.findById(dataset.getId());
        return convertToDTO(created, false);
    }

    /** 仅投影后的数据点，不带各维度原始值（供 /visualization/data-points 使用） */
    public List<DatasetDTO.DataPoint> getDataPoints(Long id) {
        Dataset dataset = datasetRepository.findById(id);
        if (dataset == null) {
            throw new IllegalArgumentException("Dataset not found with id: " + id);
        }
        List<DatasetDTO.FeatureInfo> features = parseFeatures(dataset.getFeatures());
        Projection projection = buildProjection(features);
        return projectDataPoints(parseRows(dataset.getDataContent()), projection, null);
    }

    private DatasetDTO convertToDTO(Dataset dataset, boolean withDimensionValues) {
        DatasetDTO dto = new DatasetDTO();
        dto.setId(dataset.getId());
        dto.setName(dataset.getName());
        dto.setDescription(dataset.getDescription());
        dto.setCategory(dataset.getCategory());
        dto.setFeatureCount(dataset.getFeatureCount());
        dto.setSampleCount(dataset.getSampleCount());

        List<DatasetDTO.FeatureInfo> features = parseFeatures(dataset.getFeatures());
        Projection projection = buildProjection(features);
        dto.setFeatures(projectFeatures(features, projection));
        dto.setDataPoints(projectDataPoints(parseRows(dataset.getDataContent()), projection,
                withDimensionValues ? features : null));
        if (withDimensionValues) {
            dto.setDimensions(features);
        }

        return dto;
    }

    private List<DatasetDTO.FeatureInfo> parseFeatures(String featuresJson) {
        if (featuresJson == null || featuresJson.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(featuresJson, new TypeReference<List<DatasetDTO.FeatureInfo>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private List<LinkedHashMap<String, Object>> parseRows(String dataContent) {
        if (dataContent == null || dataContent.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(dataContent, new TypeReference<List<LinkedHashMap<String, Object>>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private Projection buildProjection(List<DatasetDTO.FeatureInfo> features) {
        if (features.isEmpty()) {
            return new Projection("x", "y", "label");
        }

        String labelName = features.stream()
                .filter(feature -> "label".equals(feature.getType()))
                .map(DatasetDTO.FeatureInfo::getName)
                .findFirst()
                .orElse(null);
        String xName = preferredFeature(features, List.of("rm", "x", "petal_length", "sepal_length"));
        String yName = preferredFeature(features, List.of("y", "petal_width", "sepal_width"));

        if (xName == null) {
            xName = firstNumericFeature(features, null);
        }
        if (yName == null && labelName != null) {
            yName = labelName;
        }
        if (yName == null) {
            yName = firstNumericFeature(features, xName);
        }

        return new Projection(xName, yName, labelName);
    }

    private String preferredFeature(List<DatasetDTO.FeatureInfo> features, List<String> preferredNames) {
        for (String preferredName : preferredNames) {
            String matched = features.stream()
                    .filter(feature -> !"label".equals(feature.getType()))
                    .map(DatasetDTO.FeatureInfo::getName)
                    .filter(preferredName::equals)
                    .findFirst()
                    .orElse(null);
            if (matched != null) {
                return matched;
            }
        }
        return null;
    }

    private String firstNumericFeature(List<DatasetDTO.FeatureInfo> features, String excludedName) {
        return features.stream()
                .filter(feature -> !"label".equals(feature.getType()))
                .map(DatasetDTO.FeatureInfo::getName)
                .filter(name -> excludedName == null || !excludedName.equals(name))
                .findFirst()
                .orElse(null);
    }

    private List<DatasetDTO.FeatureInfo> projectFeatures(List<DatasetDTO.FeatureInfo> features, Projection projection) {
        List<DatasetDTO.FeatureInfo> projected = new ArrayList<>();
        projected.add(projectFeature(features, projection.xName(), "x", "Feature X"));
        projected.add(projectFeature(features, projection.yName(), "y", "Feature Y"));
        return projected;
    }

    private DatasetDTO.FeatureInfo projectFeature(
            List<DatasetDTO.FeatureInfo> features,
            String sourceName,
            String fallbackName,
            String fallbackDisplayName) {
        DatasetDTO.FeatureInfo original = features.stream()
                .filter(feature -> sourceName != null && sourceName.equals(feature.getName()))
                .findFirst()
                .orElse(null);
        DatasetDTO.FeatureInfo projected = new DatasetDTO.FeatureInfo();
        projected.setName(sourceName == null ? fallbackName : sourceName);
        projected.setDisplayName(original == null ? fallbackDisplayName : original.getDisplayName());
        projected.setType(original == null ? "number" : original.getType());
        return projected;
    }

    private List<DatasetDTO.DataPoint> projectDataPoints(
            List<LinkedHashMap<String, Object>> rows,
            Projection projection,
            List<DatasetDTO.FeatureInfo> valueFeatures) {
        List<DatasetDTO.DataPoint> points = new ArrayList<>();
        for (LinkedHashMap<String, Object> row : rows) {
            Double x = asDouble(row.get(projection.xName()));
            Double y = asDouble(row.get(projection.yName()));

            if (x == null || y == null) {
                List<Double> numericValues = row.entrySet().stream()
                        .filter(entry -> projection.labelName() == null || !projection.labelName().equals(entry.getKey()))
                        .map(entry -> asDouble(entry.getValue()))
                        .filter(value -> value != null)
                        .toList();
                if (x == null && !numericValues.isEmpty()) {
                    x = numericValues.get(0);
                }
                if (y == null && numericValues.size() >= 2) {
                    y = numericValues.get(1);
                }
            }

            if (x == null || y == null) {
                continue;
            }

            DatasetDTO.DataPoint point = new DatasetDTO.DataPoint();
            point.setX(x);
            point.setY(y);
            Object rawLabel = projection.labelName() == null ? row.get("label") : row.get(projection.labelName());
            if (rawLabel != null && (projection.labelName() == null || !projection.labelName().equals(projection.yName()))) {
                point.setLabel(String.valueOf(rawLabel));
            }
            Integer clusterId = asInteger(row.get("clusterId"));
            if (clusterId == null) {
                clusterId = asInteger(row.get("cluster_id"));
            }
            point.setClusterId(clusterId);
            if (valueFeatures != null) {
                point.setValues(buildRowValues(row, valueFeatures));
            }
            points.add(point);
        }
        return points;
    }

    /**
     * 取该行在声明维度上的原始数值，按 features 的声明顺序输出。
     * 按元数据遍历而非 row.keySet()，可避免 clusterId 之类的非声明键混入；
     * 文本型 label（如 iris 的 species）因无法转成数值而自动跳过，数值型 label（如 medv）保留。
     */
    private Map<String, Double> buildRowValues(
            LinkedHashMap<String, Object> row,
            List<DatasetDTO.FeatureInfo> features) {
        Map<String, Double> values = new LinkedHashMap<>();
        for (DatasetDTO.FeatureInfo feature : features) {
            Double value = asDouble(row.get(feature.getName()));
            if (value != null) {
                values.put(feature.getName(), value);
            }
        }
        return values;
    }

    private Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Integer asInteger(Object value) {
        Double numeric = asDouble(value);
        return numeric == null ? null : numeric.intValue();
    }

    private record Projection(String xName, String yName, String labelName) {}
}
