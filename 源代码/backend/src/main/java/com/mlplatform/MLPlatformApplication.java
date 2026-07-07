package com.mlplatform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.CommandLineRunner;

import com.mlplatform.repository.AlgorithmRepository;
import com.mlplatform.model.Algorithm;

import java.util.List;

@SpringBootApplication
@MapperScan("com.mlplatform.repository")
public class MLPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(MLPlatformApplication.class, args);
    }

    @Bean
    public CommandLineRunner ensureAlgorithms(AlgorithmRepository algorithmRepository) {
        return args -> {
            try {
                List<String> needed = List.of("pca", "random_forest");
                for (String name : needed) {
                    var existing = algorithmRepository.findByName(name);
                    if (existing == null) {
                        Algorithm a = new Algorithm();
                        if ("pca".equals(name)) {
                            a.setName("pca");
                            a.setDisplayName("PCA");
                            a.setDescription("主成分分析，用于降维和可视化");
                            a.setCategory("unsupervised");
                            a.setType("pca");
                            a.setParameters("[{\"name\":\"n_components\",\"displayName\":\"主成分数\",\"type\":\"number\",\"default\":2,\"min\":1,\"max\":10,\"step\":1}]");
                        } else {
                            a.setName("random_forest");
                            a.setDisplayName("Random Forest");
                            a.setDescription("随机森林（简化模拟），用于分类/回归的集成方法");
                            a.setCategory("supervised");
                            a.setType("ensemble");
                            a.setParameters("[{\"name\":\"n_estimators\",\"displayName\":\"树的数量\",\"type\":\"number\",\"default\":5,\"min\":1,\"max\":100,\"step\":1}]");
                        }
                        algorithmRepository.insert(a);
                    }
                }
            } catch (Exception e) {
                System.out.println("Failed to ensure algorithms: " + e.getMessage());
            }
        };
    }
}