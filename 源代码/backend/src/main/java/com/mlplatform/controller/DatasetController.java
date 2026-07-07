package com.mlplatform.controller;

import com.mlplatform.dto.ApiResponse;
import com.mlplatform.dto.DatasetDTO;
import com.mlplatform.model.Dataset;
import com.mlplatform.service.DatasetService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/datasets")
public class DatasetController {

    private final DatasetService datasetService;

    public DatasetController(DatasetService datasetService) {
        this.datasetService = datasetService;
    }

    @GetMapping
    public ApiResponse<List<DatasetDTO>> getAllDatasets() {
        return ApiResponse.success(datasetService.getAllDatasets());
    }

    @GetMapping("/{id}")
    public ApiResponse<DatasetDTO> getDatasetById(@PathVariable Long id) {
        return ApiResponse.success(datasetService.getDatasetById(id));
    }

    @GetMapping("/category/{category}")
    public ApiResponse<List<DatasetDTO>> getDatasetsByCategory(@PathVariable String category) {
        return ApiResponse.success(datasetService.getDatasetsByCategory(category));
    }

    @PostMapping("/upload")
    public ApiResponse<DatasetDTO> uploadDataset(@RequestParam("file") MultipartFile file,
                                                   @RequestParam("name") String name,
                                                   @RequestParam("description") String description,
                                                   @RequestParam("category") String category) {
        // Parse CSV and create dataset
        Dataset dataset = new Dataset();
        dataset.setName(name);
        dataset.setDescription(description);
        dataset.setCategory(category);
        dataset.setFileName(file.getOriginalFilename());

        DatasetDTO created = datasetService.createDataset(dataset);
        return ApiResponse.success("Dataset uploaded successfully", created);
    }

    @PostMapping
    public ApiResponse<DatasetDTO> createDataset(@RequestBody Dataset dataset) {
        return ApiResponse.success(datasetService.createDataset(dataset));
    }
}