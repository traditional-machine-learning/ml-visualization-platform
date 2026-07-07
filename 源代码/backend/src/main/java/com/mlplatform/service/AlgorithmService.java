package com.mlplatform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mlplatform.dto.AlgorithmDTO;
import com.mlplatform.model.Algorithm;
import com.mlplatform.repository.AlgorithmRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AlgorithmService {

    private final AlgorithmRepository algorithmRepository;
    private final ObjectMapper objectMapper;

    public AlgorithmService(AlgorithmRepository algorithmRepository, ObjectMapper objectMapper) {
        this.algorithmRepository = algorithmRepository;
        this.objectMapper = objectMapper;
    }

    public List<AlgorithmDTO> getAllAlgorithms() {
        return algorithmRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public AlgorithmDTO getAlgorithmById(Long id) {
        Algorithm algorithm = algorithmRepository.findById(id);
        if (algorithm == null) {
            throw new IllegalArgumentException("Algorithm not found with id: " + id);
        }
        return convertToDTO(algorithm);
    }

    public List<AlgorithmDTO> getAlgorithmsByCategory(String category) {
        return algorithmRepository.findByCategory(category).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public AlgorithmDTO createAlgorithm(Algorithm algorithm) {
        algorithmRepository.insert(algorithm);
        Algorithm inserted = algorithmRepository.findByName(algorithm.getName());
        if (inserted == null) {
            throw new IllegalStateException("Failed to retrieve inserted algorithm: " + algorithm.getName());
        }
        return convertToDTO(inserted);
    }

    private AlgorithmDTO convertToDTO(Algorithm algorithm) {
        AlgorithmDTO dto = new AlgorithmDTO();
        dto.setId(algorithm.getId());
        dto.setName(algorithm.getName());
        dto.setDisplayName(algorithm.getDisplayName());
        dto.setDescription(algorithm.getDescription());
        dto.setCategory(algorithm.getCategory());
        dto.setType(algorithm.getType());

        try {
            if (algorithm.getParameters() != null) {
                List<AlgorithmDTO.ParameterDef> parameters = objectMapper.readValue(
                        algorithm.getParameters(),
                        new TypeReference<List<AlgorithmDTO.ParameterDef>>() {}
                );
                normalizeParameterNames(algorithm.getName(), parameters);
                dto.setParameters(parameters);
            }
        } catch (Exception e) {
            dto.setParameters(new ArrayList<>());
        }

        return dto;
    }

    private void normalizeParameterNames(String algorithmName, List<AlgorithmDTO.ParameterDef> parameters) {
        if (!"svm".equals(algorithmName)) {
            return;
        }

        for (AlgorithmDTO.ParameterDef parameter : parameters) {
            if ("C".equals(parameter.getName())) {
                parameter.setName("c");
                return;
            }
        }
    }
}
