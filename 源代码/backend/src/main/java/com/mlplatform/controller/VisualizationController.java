package com.mlplatform.controller;

import com.mlplatform.dto.ApiResponse;
import com.mlplatform.dto.DatasetDTO;
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
