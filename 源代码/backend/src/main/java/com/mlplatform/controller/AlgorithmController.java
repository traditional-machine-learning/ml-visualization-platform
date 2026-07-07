package com.mlplatform.controller;

import com.mlplatform.dto.ApiResponse;
import com.mlplatform.dto.AlgorithmDTO;
import com.mlplatform.dto.TrainingSimulationResponse;
import com.mlplatform.model.Algorithm;
import com.mlplatform.service.AlgorithmService;
import com.mlplatform.service.TrainingSimulationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/algorithms")
public class AlgorithmController {

    private final AlgorithmService algorithmService;
    private final TrainingSimulationService trainingSimulationService;

    public AlgorithmController(AlgorithmService algorithmService, TrainingSimulationService trainingSimulationService) {
        this.algorithmService = algorithmService;
        this.trainingSimulationService = trainingSimulationService;
    }

    @GetMapping
    public ApiResponse<List<AlgorithmDTO>> getAllAlgorithms() {
        return ApiResponse.success(algorithmService.getAllAlgorithms());
    }

    @GetMapping("/{id}")
    public ApiResponse<AlgorithmDTO> getAlgorithmById(@PathVariable Long id) {
        return ApiResponse.success(algorithmService.getAlgorithmById(id));
    }

    @GetMapping("/category/{category}")
    public ApiResponse<List<AlgorithmDTO>> getAlgorithmsByCategory(@PathVariable String category) {
        return ApiResponse.success(algorithmService.getAlgorithmsByCategory(category));
    }

    @PostMapping
    public ApiResponse<AlgorithmDTO> createAlgorithm(@RequestBody Algorithm algorithm) {
        return ApiResponse.success(algorithmService.createAlgorithm(algorithm));
    }

    // Reserved for Team Member 2 - Training Control
    @PostMapping("/{id}/train")
    public ApiResponse<TrainingSimulationResponse> startTraining(@PathVariable Long id,
                                                                 @RequestBody TrainingRequest request) {
        return ApiResponse.success(trainingSimulationService.startTraining(
                id,
                request.getDatasetId(),
                request.getParameters()
        ));
    }

    @PostMapping("/{id}/step")
    public ApiResponse<TrainingSimulationResponse> trainStep(@PathVariable Long id,
                                                             @RequestBody TrainingRequest request) {
        return ApiResponse.success(trainingSimulationService.stepTraining(
                id,
                request.getSessionId(),
                request.getDatasetId(),
                request.getParameters()
        ));
    }

    @PostMapping("/{id}/pause")
    public ApiResponse<TrainingSimulationResponse> pauseTraining(@PathVariable Long id,
                                                                 @RequestBody PauseTrainingRequest request) {
        return ApiResponse.success(trainingSimulationService.pauseTraining(id, request.getSessionId()));
    }

    // Reserved for Team Member 3 - Prediction and Evaluation
    @PostMapping("/{id}/predict")
    public ApiResponse<String> predict(@PathVariable Long id,
                                        @RequestBody PredictRequest request) {
        // Prediction endpoint - to be implemented by Team Member 3
        return ApiResponse.success("Prediction - to be implemented by Team Member 3");
    }

    // New: start PCA transformation / analysis (Team Member 4)
    @PostMapping("/{id}/train-pca")
    public ApiResponse<TrainingSimulationResponse> startPca(@PathVariable Long id,
                                                           @RequestBody TrainingRequest request) {
        return ApiResponse.success(trainingSimulationService.startPca(id, request.getDatasetId(), request.getParameters()));
    }

    // New: start Random Forest (simulated) training (Team Member 4)
    @PostMapping("/{id}/train-rf")
    public ApiResponse<TrainingSimulationResponse> startRandomForest(@PathVariable Long id,
                                                                     @RequestBody TrainingRequest request) {
        return ApiResponse.success(trainingSimulationService.startRandomForest(id, request.getDatasetId(), request.getParameters()));
    }

    // Request DTOs for other team members
    public static class TrainingRequest {
        private Long sessionId;
        private Long datasetId;
        private String parameters;

        public Long getSessionId() { return sessionId; }
        public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
        public Long getDatasetId() { return datasetId; }
        public void setDatasetId(Long datasetId) { this.datasetId = datasetId; }
        public String getParameters() { return parameters; }
        public void setParameters(String parameters) { this.parameters = parameters; }
    }

    public static class PauseTrainingRequest {
        private Long sessionId;

        public Long getSessionId() { return sessionId; }
        public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    }

    public static class PredictRequest {
        private Long sessionId;
        private String inputData;

        public Long getSessionId() { return sessionId; }
        public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
        public String getInputData() { return inputData; }
        public void setInputData(String inputData) { this.inputData = inputData; }
    }
}
