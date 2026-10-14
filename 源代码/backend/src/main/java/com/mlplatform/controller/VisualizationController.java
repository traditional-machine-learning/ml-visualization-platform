package com.mlplatform.controller;

import com.mlplatform.dto.ApiResponse;
import com.mlplatform.dto.DatasetDTO;
import com.mlplatform.dto.ProjectionDTO;
import com.mlplatform.dto.TrainingSimulationResponse;
import com.mlplatform.service.DatasetService;
import com.mlplatform.service.TrainingSimulationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/visualization")
public class VisualizationController {

    private final DatasetService datasetService;
    private final TrainingSimulationService trainingSimulationService;

    public VisualizationController(DatasetService datasetService, TrainingSimulationService trainingSimulationService) {
        this.datasetService = datasetService;
        this.trainingSimulationService = trainingSimulationService;
    }

    @GetMapping("/data-points/{datasetId}")
    public ApiResponse<List<DatasetDTO.DataPoint>> getDataPoints(@PathVariable Long datasetId) {
        return ApiResponse.success(datasetService.getDataPoints(datasetId));
    }

    // Reserved for Team Member 2 - Decision boundary visualization
    @GetMapping("/decision-boundary/{sessionId}")
    public ApiResponse<TrainingSimulationResponse.ModelData> getDecisionBoundary(@PathVariable Long sessionId) {
        return ApiResponse.success(trainingSimulationService.getModelData(sessionId));
    }

    /**
     * 把模型投影到用户所选的两个维度上。
     * 线性回归 / 逻辑回归 / K-Means 会按全维度模型重新求平面切片；
     * 决策树 / SVM / 随机森林只有在两维等于训练维度时才回放结果，否则 kind = "none"。
     */
    @GetMapping("/projection/{sessionId}")
    public ApiResponse<ProjectionDTO> getProjection(@PathVariable Long sessionId,
                                                    @RequestParam String xName,
                                                    @RequestParam String yName) {
        return ApiResponse.success(trainingSimulationService.projectModel(sessionId, xName, yName));
    }

    // Reserved for Team Member 3 - Model explanation
    @GetMapping("/model-structure/{sessionId}")
    public ApiResponse<String> getModelStructure(@PathVariable Long sessionId) {
        // Model structure (e.g., decision tree visualization) - to be implemented by Team Member 3
        return ApiResponse.success("Model structure - to be implemented by Team Member 3");
    }

    // Reserved for Team Member 2 - Training metrics visualization
    @GetMapping("/training-metrics/{sessionId}")
    public ApiResponse<TrainingSimulationResponse.Metrics> getTrainingMetrics(@PathVariable Long sessionId) {
        return ApiResponse.success(trainingSimulationService.getMetrics(sessionId));
    }
}
