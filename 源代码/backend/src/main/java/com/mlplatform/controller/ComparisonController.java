package com.mlplatform.controller;

import com.mlplatform.dto.ApiResponse;
import com.mlplatform.dto.TrainingSimulationResponse;
import com.mlplatform.service.TrainingSimulationService;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ComparisonController {

    private final TrainingSimulationService trainingSimulationService;
    private final com.mlplatform.repository.AlgorithmRepository algorithmRepository;

    public ComparisonController(TrainingSimulationService trainingSimulationService,
                                com.mlplatform.repository.AlgorithmRepository algorithmRepository) {
        this.trainingSimulationService = trainingSimulationService;
        this.algorithmRepository = algorithmRepository;
    }

    @PostMapping("/comparison")
    public ApiResponse<List<TrainingSimulationResponse>> compareModels(@RequestBody CompareRequest request) {
        List<TrainingSimulationResponse> results = new ArrayList<>();
        if (request == null || request.items == null) {
            return ApiResponse.success(results);
        }
        for (CompareItem item : request.items) {
            // determine algorithm and delegate to appropriate simulation
            com.mlplatform.model.Algorithm algo = null;
            try {
                algo = algorithmRepository.findById(item.algorithmId);
            } catch (Exception ignored) {}
            String name = algo == null ? "" : (algo.getName() == null ? "" : algo.getName());
            String params = item.parameters == null ? "{}" : item.parameters;
            TrainingSimulationResponse resp;
            if ("pca".equals(name)) {
                resp = trainingSimulationService.startPca(item.algorithmId, item.datasetId, params);
            } else if ("random_forest".equals(name)) {
                resp = trainingSimulationService.startRandomForest(item.algorithmId, item.datasetId, params);
            } else {
                resp = trainingSimulationService.startTraining(item.algorithmId, item.datasetId, params);
            }
            results.add(resp);
        }
        return ApiResponse.success(results);
    }

    public static class CompareRequest {
        public List<CompareItem> items;
    }

    public static class CompareItem {
        public Long algorithmId;
        public Long datasetId;
        public String parameters;
    }
}
