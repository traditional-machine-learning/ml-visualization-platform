package com.mlplatform.controller;

import com.mlplatform.dto.ApiResponse;
import com.mlplatform.dto.TrainingSimulationResponse;
import com.mlplatform.service.TrainingSimulationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/feature-engineering")
public class FeatureEngineeringController {

    private final TrainingSimulationService trainingSimulationService;

    public FeatureEngineeringController(TrainingSimulationService trainingSimulationService) {
        this.trainingSimulationService = trainingSimulationService;
    }

    @PostMapping("/select")
    public ApiResponse<TrainingSimulationResponse> selectFeatures(@RequestBody SelectRequest request) {
        if (request == null || request.selectedFeatures == null) {
            return ApiResponse.success(null);
        }
        TrainingSimulationResponse resp = trainingSimulationService.simulateFeatureSelection(
                request.algorithmId, request.datasetId, request.selectedFeatures, request.parameters == null ? "{}" : request.parameters);
        return ApiResponse.success(resp);
    }

    public static class SelectRequest {
        public Long algorithmId;
        public Long datasetId;
        public List<String> selectedFeatures;
        public String parameters;
    }
}
