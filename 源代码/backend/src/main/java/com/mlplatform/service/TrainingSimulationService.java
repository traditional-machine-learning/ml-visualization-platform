package com.mlplatform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mlplatform.dto.TrainingSimulationResponse;
import com.mlplatform.model.Algorithm;
import com.mlplatform.model.Dataset;
import com.mlplatform.model.TrainingSession;
import com.mlplatform.repository.AlgorithmRepository;
import com.mlplatform.repository.DatasetRepository;
import com.mlplatform.repository.TrainingSessionRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TrainingSimulationService {
    private static final int DEFAULT_TOTAL_STEPS = 100;
    private static final double EPSILON = 1.0e-9;

    private final AlgorithmRepository algorithmRepository;
    private final DatasetRepository datasetRepository;
    private final TrainingSessionRepository trainingSessionRepository;
    private final ObjectMapper objectMapper;

    public TrainingSimulationService(
            AlgorithmRepository algorithmRepository,
            DatasetRepository datasetRepository,
            TrainingSessionRepository trainingSessionRepository,
            ObjectMapper objectMapper) {
        this.algorithmRepository = algorithmRepository;
        this.datasetRepository = datasetRepository;
        this.trainingSessionRepository = trainingSessionRepository;
        this.objectMapper = objectMapper;
    }

    public TrainingSimulationResponse startTraining(Long algorithmId, Long datasetId, String parameters) {
        Algorithm algorithm = requireAlgorithm(algorithmId);
        Dataset dataset = requireDataset(datasetId);

        int totalSteps = getTotalStepsFromParameters(parameters, algorithm);
        TrainingSession session = new TrainingSession();
        session.setAlgorithmId(algorithmId);
        session.setDatasetId(datasetId);
        session.setParameters(normalizeParameters(parameters));
        session.setStatus("training");
        session.setCurrentStep(0);
        session.setTotalSteps(totalSteps);

        TrainingSimulationResponse.ModelData initialModel = initializeModelData(algorithm, dataset, session.getParameters());
        TrainingSimulationResponse initial = buildResponse(session, algorithm, dataset, initialModel, null);
        session.setMetrics(toJson(initial.getMetrics()));
        session.setModelData(toJson(initial.getModelData()));
        trainingSessionRepository.insert(session);

        initial.setSessionId(session.getId());
        return initial;
    }

    public TrainingSimulationResponse stepTraining(Long algorithmId, Long sessionId, Long datasetId, String parameters) {
        TrainingSession session = requireSession(sessionId);
        validateSession(session, algorithmId, datasetId);

        Algorithm algorithm = requireAlgorithm(session.getAlgorithmId());
        Dataset dataset = requireDataset(session.getDatasetId());
        String normalizedParams = normalizeParameters(parameters != null ? parameters : session.getParameters());
        session.setParameters(normalizedParams);

        int totalSteps = session.getTotalSteps() == null
                ? getTotalStepsFromParameters(normalizedParams, algorithm)
                : session.getTotalSteps();
        session.setTotalSteps(totalSteps);

        int currentStep = session.getCurrentStep() == null ? 0 : session.getCurrentStep();
        int nextStep = Math.min(totalSteps, currentStep + 1);
        TrainingSimulationResponse.ModelData modelData = readOrInitializeModelData(session, algorithm, dataset);
        StepResult stepResult = trainAlgorithmStep(algorithm, dataset, session.getParameters(), modelData);

        session.setCurrentStep(nextStep);
        session.setStatus(nextStep >= totalSteps ? "completed" : "training");

        TrainingSimulationResponse response = buildResponse(session, algorithm, dataset, stepResult.modelData, stepResult);
        session.setMetrics(toJson(response.getMetrics()));
        session.setModelData(toJson(response.getModelData()));
        trainingSessionRepository.update(session);

        return response;
    }

    public TrainingSimulationResponse pauseTraining(Long algorithmId, Long sessionId) {
        TrainingSession session = requireSession(sessionId);
        validateSession(session, algorithmId, null);

        Algorithm algorithm = requireAlgorithm(session.getAlgorithmId());
        Dataset dataset = requireDataset(session.getDatasetId());
        int currentStep = session.getCurrentStep() == null ? 0 : session.getCurrentStep();
        int totalSteps = session.getTotalSteps() == null
                ? getTotalStepsFromParameters(session.getParameters(), algorithm)
                : session.getTotalSteps();
        session.setStatus(currentStep >= totalSteps ? "completed" : "paused");
        session.setTotalSteps(totalSteps);

        TrainingSimulationResponse.ModelData modelData = readOrInitializeModelData(session, algorithm, dataset);
        StepResult current = evaluateCurrentState(algorithm, dataset, session.getParameters(), modelData);
        TrainingSimulationResponse response = buildResponse(session, algorithm, dataset, modelData, current);
        session.setMetrics(toJson(response.getMetrics()));
        session.setModelData(toJson(response.getModelData()));
        trainingSessionRepository.update(session);

        return response;
    }

    public TrainingSimulationResponse.ModelData getModelData(Long sessionId) {
        TrainingSession session = requireSession(sessionId);
        if (session.getModelData() != null) {
            return fromJson(session.getModelData(), TrainingSimulationResponse.ModelData.class);
        }
        return initializeModelData(
                requireAlgorithm(session.getAlgorithmId()),
                requireDataset(session.getDatasetId()),
                session.getParameters());
    }

    public TrainingSimulationResponse.Metrics getMetrics(Long sessionId) {
        TrainingSession session = requireSession(sessionId);
        if (session.getMetrics() != null) {
            return fromJson(session.getMetrics(), TrainingSimulationResponse.Metrics.class);
        }
        Algorithm algorithm = requireAlgorithm(session.getAlgorithmId());
        Dataset dataset = requireDataset(session.getDatasetId());
        StepResult current = evaluateCurrentState(algorithm, dataset, session.getParameters(), getModelData(sessionId));
        return buildMetrics(algorithm, session.getCurrentStep(), session.getTotalSteps(), current);
    }

    // ----- Advanced features: PCA and Random Forest (simulated) -----
    public TrainingSimulationResponse startPca(Long algorithmId, Long datasetId, String parameters) {
        Algorithm algorithm = requireAlgorithm(algorithmId);
        Dataset dataset = requireDataset(datasetId);

        TrainingSimulationResponse.ModelData modelData = new TrainingSimulationResponse.ModelData();
        // parse numeric matrix from dataset
        List<LinkedHashMap<String, Object>> rows = parseRows(dataset.getDataContent());
        List<FeatureInfo> features = parseFeatures(dataset.getFeatures());
        List<String> numericNames = features.stream()
                .filter(f -> !"label".equals(f.type()))
                .map(FeatureInfo::name)
                .toList();
        double[][] matrix = buildNumericMatrix(rows, numericNames);
        if (matrix.length == 0 || numericNames.size() == 0) {
            modelData.setPcaComponents(List.of());
            modelData.setPcaTransformed(List.of());
        } else {
            int k = Math.min(2, numericNames.size());
            PcaResult pca = computePca(matrix, k);
            // components: each component is array of loadings per original feature
            List<List<Double>> comps = new java.util.ArrayList<>();
            for (double[] comp : pca.components) {
                List<Double> row = new java.util.ArrayList<>();
                for (double v : comp) row.add(round(v));
                comps.add(row);
            }
            List<List<Double>> transformed = new java.util.ArrayList<>();
            for (double[] proj : pca.transformed) {
                List<Double> pt = new java.util.ArrayList<>();
                for (double v : proj) pt.add(round(v));
                transformed.add(pt);
            }
            modelData.setPcaComponents(comps);
            modelData.setPcaTransformed(transformed);
        }

        TrainingSimulationResponse response = new TrainingSimulationResponse();
        response.setAlgorithmId(algorithmId);
        response.setDatasetId(datasetId);
        response.setStatus("completed");
        response.setCurrentStep(0);
        response.setTotalSteps(0);
        response.setLoss(0.0);
        response.setAccuracy(0.0);
        response.setScoreLabel("ExplainedVar");
        response.setModelData(modelData);
        return response;
    }

    public TrainingSimulationResponse startRandomForest(Long algorithmId, Long datasetId, String parameters) {
        Algorithm algorithm = requireAlgorithm(algorithmId);
        Dataset dataset = requireDataset(datasetId);
        int nEstimators = clampInt(getNumericParameter(parameters, "n_estimators", 5), 1, 20);

        DataSet2d data = loadDataSet(dataset, algorithm);
        List<DecisionSplit> trees = new ArrayList<>();
        List<String> featureUsed = new ArrayList<>();
        for (int t = 0; t < nEstimators; t++) {
            // bootstrap sample indices
            List<DataPoint2d> sample = new ArrayList<>();
            for (int i = 0; i < data.points.size(); i++) {
                int idx = (int) Math.floor(Math.random() * data.points.size());
                sample.add(data.points.get(idx));
            }
            DecisionSplit split = findBestDecisionSplit(sample, getDecisionTreeConfig(parameters));
            trees.add(split);
            featureUsed.add(split.feature);
        }

        // aggregate predictions
        int correct = 0;
        for (DataPoint2d point : data.points) {
            Map<Integer, Integer> votes = new HashMap<>();
            for (DecisionSplit split : trees) {
                int pred = isLeft(point, split.feature, split.threshold) ? split.leftClassIndex : split.rightClassIndex;
                votes.merge(pred, 1, Integer::sum);
            }
            int finalPred = votes.entrySet().stream().max((a, b) -> Integer.compare(a.getValue(), b.getValue())).map(Map.Entry::getKey).orElse(0);
            if (finalPred == point.classIndex) correct += 1;
        }
        double accuracy = data.points.isEmpty() ? 0.0 : (double) correct / data.points.size();

        // feature importances (frequency)
        Map<String, Integer> freq = new HashMap<>();
        for (String f : featureUsed) freq.merge(f, 1, Integer::sum);
        List<TrainingSimulationResponse.FeatureImportance> importances = new ArrayList<>();
        int total = trees.size() == 0 ? 1 : trees.size();
        for (String f : List.of("x", "y")) {
            double imp = freq.getOrDefault(f, 0) / (double) total;
            TrainingSimulationResponse.FeatureImportance fi = new TrainingSimulationResponse.FeatureImportance();
            fi.setFeature(f);
            fi.setImportance(round(imp));
            importances.add(fi);
        }

        TrainingSimulationResponse.ModelData modelData = new TrainingSimulationResponse.ModelData();
        modelData.setFeatureImportances(importances);

        TrainingSimulationResponse response = new TrainingSimulationResponse();
        response.setAlgorithmId(algorithmId);
        response.setDatasetId(datasetId);
        response.setStatus("completed");
        response.setCurrentStep(0);
        response.setTotalSteps(0);
        response.setLoss(0.0);
        response.setAccuracy(round(accuracy));
        response.setScoreLabel("Accuracy");
        response.setModelData(modelData);
        return response;
    }

    public TrainingSimulationResponse simulateFeatureSelection(Long algorithmId, Long datasetId, List<String> selectedFeatures, String parameters) {
        Algorithm algorithm = requireAlgorithm(algorithmId);
        Dataset dataset = requireDataset(datasetId);
        // build filtered dataset
        List<LinkedHashMap<String, Object>> rows = parseRows(dataset.getDataContent());
        List<LinkedHashMap<String, Object>> filtered = new ArrayList<>();
        for (LinkedHashMap<String, Object> row : rows) {
            LinkedHashMap<String, Object> nr = new LinkedHashMap<>();
            for (String key : row.keySet()) {
                if (selectedFeatures.contains(key) || "label".equals(key)) {
                    nr.put(key, row.get(key));
                }
            }
            filtered.add(nr);
        }
        Dataset tmp = new Dataset();
        tmp.setId(dataset.getId());
        tmp.setName(dataset.getName());
        tmp.setFeatures(buildFeaturesJson(selectedFeatures, dataset));
        try {
            tmp.setDataContent(objectMapper.writeValueAsString(filtered));
        } catch (Exception e) {
            tmp.setDataContent("[]");
        }

        // Evaluate using existing evaluation logic
        TrainingSimulationResponse.ModelData modelData = initializeModelData(algorithm, tmp, "{}");
        StepResult result = evaluateCurrentState(algorithm, tmp, parameters, modelData);

        TrainingSimulationResponse response = new TrainingSimulationResponse();
        response.setAlgorithmId(algorithmId);
        response.setDatasetId(datasetId);
        response.setStatus("completed");
        response.setCurrentStep(0);
        response.setTotalSteps(0);
        response.setLoss(round(result.loss));
        response.setAccuracy(round(result.score));
        response.setScoreLabel(result.scoreLabel);
        response.setModelData(result.modelData);
        return response;
    }

    private TrainingSimulationResponse buildResponse(
            TrainingSession session,
            Algorithm algorithm,
            Dataset dataset,
            TrainingSimulationResponse.ModelData modelData,
            StepResult stepResult) {
        StepResult current = stepResult == null
                ? evaluateCurrentState(algorithm, dataset, session.getParameters(), modelData)
                : stepResult;

        int totalSteps = session.getTotalSteps() == null
                ? getTotalStepsFromParameters(session.getParameters(), algorithm)
                : session.getTotalSteps();

        TrainingSimulationResponse response = new TrainingSimulationResponse();
        response.setSessionId(session.getId());
        response.setAlgorithmId(session.getAlgorithmId());
        response.setDatasetId(session.getDatasetId());
        response.setStatus(session.getStatus());
        response.setCurrentStep(session.getCurrentStep() == null ? 0 : session.getCurrentStep());
        response.setTotalSteps(totalSteps);
        response.setLoss(current.loss);
        response.setAccuracy(current.score);
        response.setScoreLabel(current.scoreLabel);
        response.setMetrics(buildMetrics(algorithm, response.getCurrentStep(), totalSteps, current));
        response.setModelData(current.modelData);
        return response;
    }

    private TrainingSimulationResponse.Metrics buildMetrics(
            Algorithm algorithm,
            Integer currentStep,
            int totalSteps,
            StepResult current) {
        int safeCurrent = currentStep == null ? 0 : currentStep;
        int safeTotal = totalSteps;
        List<TrainingSimulationResponse.MetricPoint> points = new ArrayList<>();

        for (int step = 1; step <= safeTotal; step++) {
            double visibleProgress = safeTotal == 0 ? 0.0 : (double) Math.min(step, safeCurrent) / safeTotal;
            TrainingSimulationResponse.MetricPoint point = new TrainingSimulationResponse.MetricPoint();
            point.setStep(step);
            if (step <= safeCurrent || safeCurrent == 0) {
                point.setLoss(step == safeCurrent ? current.loss : interpolateLoss(current.loss, visibleProgress));
                point.setScore(step == safeCurrent ? current.score : interpolateScore(current.score, visibleProgress));
            } else {
                point.setLoss(current.loss);
                point.setScore(current.score);
            }
            points.add(point);
        }

        TrainingSimulationResponse.Metrics metrics = new TrainingSimulationResponse.Metrics();
        metrics.setPoints(points);
        metrics.setLossLabel(getLossLabel(algorithm));
        metrics.setScoreLabel(current.scoreLabel);
        return metrics;
    }

    private TrainingSimulationResponse.ModelData initializeModelData(Algorithm algorithm, Dataset dataset, String parameters) {
        TrainingSimulationResponse.ModelData modelData = new TrainingSimulationResponse.ModelData();
        DataSet2d data = loadDataSet(dataset, algorithm);
        DataStats stats = new DataStats(data.points);
        String name = algorithm.getName() == null ? "" : algorithm.getName();
        String type = algorithm.getType() == null ? "" : algorithm.getType();

        if ("linear_regression".equals(name)) {
            modelData.setWeights(List.of(0.0));
            modelData.setBias(0.0);
            modelData.setRegressionLine(buildRegressionLine(stats, 0.0, 0.0));
        } else if ("logistic_regression".equals(name) || "svm".equals(name)) {
            modelData.setWeights(Arrays.asList(0.0, 0.0));
            modelData.setBias(0.0);
            modelData.setDecisionBoundary(buildLinearBoundary(stats, List.of(0.0, 0.0), 0.0));
        } else if ("kmeans".equals(name) || "clustering".equals(type)) {
            List<TrainingSimulationResponse.Centroid> centroids = initializeCentroids(data.points, getK(parameters));
            modelData.setCentroids(centroids);
            modelData.setAssignments(assignClusters(data.points, centroids));
        } else if ("decision_tree".equals(name)) {
            DecisionSplit split = findBestDecisionSplit(data.points, getDecisionTreeConfig(parameters));
            applyDecisionSplitModel(modelData, stats, split);
        } else if ("classification".equals(type)) {
            modelData.setWeights(Arrays.asList(0.0, 0.0));
            modelData.setBias(0.0);
            modelData.setDecisionBoundary(buildLinearBoundary(stats, List.of(0.0, 0.0), 0.0));
        } else if ("regression".equals(type)) {
            modelData.setWeights(List.of(0.0));
            modelData.setBias(0.0);
            modelData.setRegressionLine(buildRegressionLine(stats, 0.0, 0.0));
        }

        return modelData;
    }

    private TrainingSimulationResponse.ModelData readOrInitializeModelData(
            TrainingSession session,
            Algorithm algorithm,
            Dataset dataset) {
        if (session.getModelData() != null && !session.getModelData().isBlank()) {
            return fromJson(session.getModelData(), TrainingSimulationResponse.ModelData.class);
        }
        return initializeModelData(algorithm, dataset, session.getParameters());
    }

    private StepResult trainAlgorithmStep(
            Algorithm algorithm,
            Dataset dataset,
            String parameters,
            TrainingSimulationResponse.ModelData modelData) {
        String name = algorithm.getName() == null ? "" : algorithm.getName();
        String type = algorithm.getType() == null ? "" : algorithm.getType();

        if ("linear_regression".equals(name)) {
            return trainLinearRegression(dataset, parameters, modelData);
        }
        if ("logistic_regression".equals(name)) {
            return trainLogisticRegression(dataset, parameters, modelData);
        }
        if ("kmeans".equals(name) || "clustering".equals(type)) {
            return trainKMeans(dataset, parameters, modelData);
        }
        if ("decision_tree".equals(name)) {
            return trainDecisionTree(dataset, parameters, modelData);
        }
        if ("svm".equals(name)) {
            return trainSvm(dataset, parameters, modelData);
        }
        if ("classification".equals(type)) {
            return trainLogisticRegression(dataset, parameters, modelData);
        }
        if ("regression".equals(type)) {
            return trainLinearRegression(dataset, parameters, modelData);
        }
        return evaluateCurrentState(algorithm, dataset, parameters, modelData);
    }

    private StepResult evaluateCurrentState(
            Algorithm algorithm,
            Dataset dataset,
            String parameters,
            TrainingSimulationResponse.ModelData modelData) {
        String name = algorithm.getName() == null ? "" : algorithm.getName();
        String type = algorithm.getType() == null ? "" : algorithm.getType();

        if ("linear_regression".equals(name) || "regression".equals(type)) {
            return evaluateLinearRegression(dataset, modelData);
        }
        if ("kmeans".equals(name) || "clustering".equals(type)) {
            return evaluateKMeans(dataset, modelData);
        }
        if ("decision_tree".equals(name)) {
            return evaluateDecisionTree(dataset, parameters, modelData);
        }
        if ("svm".equals(name)) {
            return evaluateSvm(dataset, parameters, modelData);
        }
        return evaluateLogisticRegression(dataset, modelData);
    }

    private StepResult trainLinearRegression(Dataset dataset, String parameters, TrainingSimulationResponse.ModelData modelData) {
        DataSet2d data = loadDataSet(dataset, null);
        DataStats stats = new DataStats(data.points);
        double w = getWeight(modelData, 0);
        double b = modelData.getBias() == null ? 0.0 : modelData.getBias();
        double learningRate = clamp(getNumericParameter(parameters, "learningRate", 0.03), 0.0001, 0.2);
        double momentum = 0.35;

        double dw = 0.0;
        double db = 0.0;
        for (DataPoint2d point : data.points) {
            double x = stats.normalizeX(point.x);
            double y = stats.normalizeTarget(point.target);
            double prediction = w * x + b;
            double error = prediction - y;
            dw += 2.0 * error * x;
            db += 2.0 * error;
        }
        int n = Math.max(1, data.points.size());
        double targetW = w - learningRate * dw / n;
        double targetB = b - learningRate * db / n;
        w = w + (targetW - w) * momentum;
        b = b + (targetB - b) * momentum;

        modelData.setWeights(List.of(round(w)));
        modelData.setBias(round(b));
        modelData.setRegressionLine(buildRegressionLine(stats, w, b));
        return evaluateLinearRegression(dataset, modelData);
    }

    private StepResult evaluateLinearRegression(Dataset dataset, TrainingSimulationResponse.ModelData modelData) {
        DataSet2d data = loadDataSet(dataset, null);
        DataStats stats = new DataStats(data.points);
        double w = getWeight(modelData, 0);
        double b = modelData.getBias() == null ? 0.0 : modelData.getBias();
        double loss = 0.0;
        double sse = 0.0;
        double sst = 0.0;

        for (DataPoint2d point : data.points) {
            double x = stats.normalizeX(point.x);
            double y = stats.normalizeTarget(point.target);
            double prediction = w * x + b;
            double error = prediction - y;
            loss += error * error;
            double rawPrediction = stats.denormalizeTarget(prediction);
            double rawError = rawPrediction - point.target;
            sse += rawError * rawError;
            double targetCenter = point.target - stats.avgTarget;
            sst += targetCenter * targetCenter;
        }

        int n = Math.max(1, data.points.size());
        double mse = loss / n;
        double r2 = sst < EPSILON ? 1.0 : 1.0 - sse / sst;
        modelData.setRegressionLine(buildRegressionLine(stats, w, b));
        return new StepResult(modelData, round(mse), round(clamp(r2, 0.0, 0.99)), "R2");
    }

    private StepResult trainLogisticRegression(Dataset dataset, String parameters, TrainingSimulationResponse.ModelData modelData) {
        DataSet2d data = loadDataSet(dataset, null);
        DataStats stats = new DataStats(data.points);
        double w1 = getWeight(modelData, 0);
        double w2 = getWeight(modelData, 1);
        double b = modelData.getBias() == null ? 0.0 : modelData.getBias();
        double learningRate = clamp(getNumericParameter(parameters, "learningRate", 0.08), 0.0001, 0.8);
        double momentum = 0.35;

        double dw1 = 0.0;
        double dw2 = 0.0;
        double db = 0.0;
        for (DataPoint2d point : data.points) {
            double x = stats.normalizeX(point.x);
            double y = stats.normalizeY(point.y);
            double target = point.classValue;
            double probability = sigmoid(w1 * x + w2 * y + b);
            double error = probability - target;
            dw1 += error * x;
            dw2 += error * y;
            db += error;
        }
        int n = Math.max(1, data.points.size());
        double targetW1 = w1 - learningRate * dw1 / n;
        double targetW2 = w2 - learningRate * dw2 / n;
        double targetB = b - learningRate * db / n;
        w1 = w1 + (targetW1 - w1) * momentum;
        w2 = w2 + (targetW2 - w2) * momentum;
        b = b + (targetB - b) * momentum;

        modelData.setWeights(Arrays.asList(round(w1), round(w2)));
        modelData.setBias(round(b));
        modelData.setDecisionBoundary(buildLinearBoundary(stats, List.of(w1, w2), b));
        return evaluateLogisticRegression(dataset, modelData);
    }

    private StepResult evaluateLogisticRegression(Dataset dataset, TrainingSimulationResponse.ModelData modelData) {
        DataSet2d data = loadDataSet(dataset, null);
        DataStats stats = new DataStats(data.points);
        double w1 = getWeight(modelData, 0);
        double w2 = getWeight(modelData, 1);
        double b = modelData.getBias() == null ? 0.0 : modelData.getBias();
        double loss = 0.0;
        int correct = 0;

        for (DataPoint2d point : data.points) {
            double x = stats.normalizeX(point.x);
            double y = stats.normalizeY(point.y);
            double target = point.classValue;
            double probability = clamp(sigmoid(w1 * x + w2 * y + b), EPSILON, 1.0 - EPSILON);
            loss += -(target * Math.log(probability) + (1.0 - target) * Math.log(1.0 - probability));
            int predicted = probability >= 0.5 ? 1 : 0;
            if (predicted == (int) target) {
                correct += 1;
            }
        }

        modelData.setDecisionBoundary(buildLinearBoundary(stats, List.of(w1, w2), b));
        double accuracy = data.points.isEmpty() ? 0.0 : (double) correct / data.points.size();
        return new StepResult(modelData, round(loss / Math.max(1, data.points.size())), round(accuracy), "Accuracy");
    }

    private StepResult trainKMeans(Dataset dataset, String parameters, TrainingSimulationResponse.ModelData modelData) {
        DataSet2d data = loadDataSet(dataset, null);
        int k = getK(parameters);
        List<TrainingSimulationResponse.Centroid> centroids = modelData.getCentroids();
        if (centroids == null || centroids.size() != k) {
            centroids = initializeCentroids(data.points, k);
        }

        List<Integer> assignments = assignClusters(data.points, centroids);
        List<TrainingSimulationResponse.Centroid> updated = recomputeCentroids(data.points, assignments, centroids);
        modelData.setCentroids(updated);
        modelData.setAssignments(assignClusters(data.points, updated));
        return evaluateKMeans(dataset, modelData);
    }

    private StepResult evaluateKMeans(Dataset dataset, TrainingSimulationResponse.ModelData modelData) {
        DataSet2d data = loadDataSet(dataset, null);
        List<TrainingSimulationResponse.Centroid> centroids = modelData.getCentroids();
        if (centroids == null || centroids.isEmpty()) {
            centroids = initializeCentroids(data.points, 3);
            modelData.setCentroids(centroids);
        }
        List<Integer> assignments = assignClusters(data.points, centroids);
        modelData.setAssignments(assignments);

        double inertia = 0.0;
        for (int i = 0; i < data.points.size(); i++) {
            DataPoint2d point = data.points.get(i);
            TrainingSimulationResponse.Centroid centroid = centroids.get(assignments.get(i));
            inertia += squaredDistance(point.x, point.y, centroid.getX(), centroid.getY());
        }

        double normalizedInertia = normalizeInertia(inertia, data.points);
        double silhouette = approximateSilhouette(data.points, assignments, centroids);
        return new StepResult(modelData, round(normalizedInertia), round(silhouette), "Silhouette");
    }

    private StepResult trainDecisionTree(Dataset dataset, String parameters, TrainingSimulationResponse.ModelData modelData) {
        DataSet2d data = loadDataSet(dataset, null);
        DataStats stats = new DataStats(data.points);
        DecisionTreeConfig config = getDecisionTreeConfig(parameters);
        DecisionSplit split = findBestDecisionSplit(data.points, config);
        applyDecisionSplitModel(modelData, stats, split);
        return evaluateDecisionTree(dataset, parameters, modelData);
    }

    private StepResult evaluateDecisionTree(Dataset dataset, String parameters, TrainingSimulationResponse.ModelData modelData) {
        DataSet2d data = loadDataSet(dataset, null);
        DataStats stats = new DataStats(data.points);
        DecisionTreeConfig config = getDecisionTreeConfig(parameters);
        DecisionSplit split = modelData.getSplitThreshold() == null
                ? findBestDecisionSplit(data.points, config)
                : buildDecisionSplit(data.points, modelData.getSplitFeature(), modelData.getSplitThreshold(), config);
        if (modelData.getSplitThreshold() != null && modelData.getSplitImpurity() != null) {
            split = new DecisionSplit(
                    split.feature,
                    split.threshold,
                    modelData.getSplitImpurity(),
                    split.leftClassIndex,
                    split.rightClassIndex);
        }
        applyDecisionSplitModel(modelData, stats, split);

        int correct = 0;
        for (DataPoint2d point : data.points) {
            int prediction = predictDecisionSplit(point, split);
            if (prediction == point.classIndex) {
                correct += 1;
            }
        }
        double accuracy = data.points.isEmpty() ? 0.0 : (double) correct / data.points.size();
        return new StepResult(modelData, round(Math.max(0.0, split.impurity)), round(accuracy), "Accuracy");
    }

    private StepResult trainSvm(Dataset dataset, String parameters, TrainingSimulationResponse.ModelData modelData) {
        String kernel = getSvmKernel(parameters);
        if ("rbf".equals(kernel)) {
            return trainRbfSvm(dataset, parameters, modelData);
        }
        if ("poly".equals(kernel)) {
            return trainPolySvm(dataset, parameters, modelData);
        }
        return trainLinearSvm(dataset, parameters, modelData);
    }

    private StepResult evaluateSvm(Dataset dataset, String parameters, TrainingSimulationResponse.ModelData modelData) {
        String kernel = getSvmKernel(parameters);
        if ("rbf".equals(kernel)) {
            return evaluateKernelSvm(dataset, parameters, modelData, "rbf");
        }
        if ("poly".equals(kernel)) {
            return evaluateKernelSvm(dataset, parameters, modelData, "poly");
        }
        return evaluateLinearSvm(dataset, modelData);
    }

    private StepResult trainLinearSvm(Dataset dataset, String parameters, TrainingSimulationResponse.ModelData modelData) {
        DataSet2d data = loadDataSet(dataset, null);
        DataStats stats = new DataStats(data.points);
        double w1 = getWeight(modelData, 0);
        double w2 = getWeight(modelData, 1);
        double b = modelData.getBias() == null ? 0.0 : modelData.getBias();
        double learningRate = clamp(getNumericParameter(parameters, "learningRate", 0.05), 0.0001, 0.5);
        double c = clamp(getNumericParameter(parameters, "c", 1.0), 0.1, 10.0);

        double dw1 = w1;
        double dw2 = w2;
        double db = 0.0;
        for (DataPoint2d point : data.points) {
            double x = stats.normalizeX(point.x);
            double y = stats.normalizeY(point.y);
            double target = point.classValue > 0.5 ? 1.0 : -1.0;
            double margin = target * (w1 * x + w2 * y + b);
            if (margin < 1.0) {
                dw1 -= c * target * x;
                dw2 -= c * target * y;
                db -= c * target;
            }
        }
        int n = Math.max(1, data.points.size());
        double targetW1 = w1 - learningRate * dw1 / n;
        double targetW2 = w2 - learningRate * dw2 / n;
        double targetB = b - learningRate * db / n;
        double momentum = 0.35;
        w1 = w1 + (targetW1 - w1) * momentum;
        w2 = w2 + (targetW2 - w2) * momentum;
        b = b + (targetB - b) * momentum;

        modelData.setWeights(Arrays.asList(round(w1), round(w2)));
        modelData.setBias(round(b));
        modelData.setDecisionBoundary(buildLinearBoundary(stats, List.of(w1, w2), b));
        return evaluateLinearSvm(dataset, modelData);
    }

    private StepResult trainRbfSvm(Dataset dataset, String parameters, TrainingSimulationResponse.ModelData modelData) {
        DataSet2d data = loadDataSet(dataset, null);
        DataStats stats = new DataStats(data.points);
        List<Double> targetCenters = classCenters(data.points, stats);
        List<Double> weights = modelData.getWeights();
        double negX = blendWeight(weights, 0, targetCenters.get(0), 0.35);
        double negY = blendWeight(weights, 1, targetCenters.get(1), 0.35);
        double posX = blendWeight(weights, 2, targetCenters.get(2), 0.35);
        double posY = blendWeight(weights, 3, targetCenters.get(3), 0.35);
        double learningRate = clamp(getNumericParameter(parameters, "learningRate", 0.05), 0.0001, 0.5);
        double c = clamp(getNumericParameter(parameters, "c", 1.0), 0.1, 10.0);
        double gamma = getSvmGamma(parameters);
        double bias = modelData.getBias() == null ? 0.0 : modelData.getBias();
        List<Double> nextWeights = Arrays.asList(negX, negY, posX, posY);

        double db = 0.0;
        for (DataPoint2d point : data.points) {
            double target = point.classValue > 0.5 ? 1.0 : -1.0;
            double margin = target * scoreRbf(stats, nextWeights, bias, gamma, point.x, point.y);
            if (margin < 1.0) {
                db -= c * target;
            }
        }
        bias -= learningRate * db / Math.max(1, data.points.size());
        double finalBias = bias;

        modelData.setWeights(nextWeights.stream().map(this::round).toList());
        modelData.setBias(round(finalBias));
        modelData.setDecisionBoundary(buildSampledBoundary(stats, (x, y) -> scoreRbf(stats, nextWeights, finalBias, gamma, x, y)));
        return evaluateKernelSvm(dataset, parameters, modelData, "rbf");
    }

    private StepResult trainPolySvm(Dataset dataset, String parameters, TrainingSimulationResponse.ModelData modelData) {
        DataSet2d data = loadDataSet(dataset, null);
        DataStats stats = new DataStats(data.points);
        double w1 = getWeight(modelData, 0);
        double w2 = getWeight(modelData, 1);
        double q1 = modelData.getWeights() != null && modelData.getWeights().size() > 2 ? getWeight(modelData, 2) : 0.35;
        double q2 = modelData.getWeights() != null && modelData.getWeights().size() > 3 ? getWeight(modelData, 3) : -0.35;
        double q3 = getWeight(modelData, 4);
        double bias = modelData.getBias() == null ? 0.0 : modelData.getBias();
        double learningRate = clamp(getNumericParameter(parameters, "learningRate", 0.05), 0.0001, 0.5);
        double c = clamp(getNumericParameter(parameters, "c", 1.0), 0.1, 10.0);
        double gamma = getSvmGamma(parameters);

        double dw1 = w1;
        double dw2 = w2;
        double dq1 = q1 * 0.2;
        double dq2 = q2 * 0.2;
        double dq3 = q3 * 0.2;
        double db = 0.0;
        for (DataPoint2d point : data.points) {
            double x = stats.normalizeX(point.x);
            double y = stats.normalizeY(point.y);
            double target = point.classValue > 0.5 ? 1.0 : -1.0;
            double margin = target * scorePoly(w1, w2, q1, q2, q3, bias, gamma, x, y);
            if (margin < 1.0) {
                dw1 -= c * target * x;
                dw2 -= c * target * y;
                dq1 -= c * target * gamma * x * x;
                dq2 -= c * target * gamma * y * y;
                dq3 -= c * target * gamma * x * y;
                db -= c * target;
            }
        }
        int n = Math.max(1, data.points.size());
        w1 -= learningRate * dw1 / n;
        w2 -= learningRate * dw2 / n;
        q1 -= learningRate * dq1 / n;
        q2 -= learningRate * dq2 / n;
        q3 -= learningRate * dq3 / n;
        bias -= learningRate * db / n;
        double finalW1 = w1;
        double finalW2 = w2;
        double finalQ1 = q1;
        double finalQ2 = q2;
        double finalQ3 = q3;
        double finalBias = bias;

        List<Double> weights = Arrays.asList(round(finalW1), round(finalW2), round(finalQ1), round(finalQ2), round(finalQ3));
        modelData.setWeights(weights);
        modelData.setBias(round(finalBias));
        modelData.setDecisionBoundary(buildSampledBoundary(stats, (rawX, rawY) -> scorePoly(
                finalW1,
                finalW2,
                finalQ1,
                finalQ2,
                finalQ3,
                finalBias,
                gamma,
                stats.normalizeX(rawX),
                stats.normalizeY(rawY))));
        return evaluateKernelSvm(dataset, parameters, modelData, "poly");
    }

    private StepResult evaluateKernelSvm(
            Dataset dataset,
            String parameters,
            TrainingSimulationResponse.ModelData modelData,
            String kernel) {
        DataSet2d data = loadDataSet(dataset, null);
        DataStats stats = new DataStats(data.points);
        double gamma = getSvmGamma(parameters);
        List<Double> weights = modelData.getWeights();
        if ("rbf".equals(kernel) && (weights == null || weights.size() < 4)) {
            weights = classCenters(data.points, stats);
            modelData.setWeights(weights.stream().map(this::round).toList());
        } else if ("poly".equals(kernel) && (weights == null || weights.size() < 5)) {
            weights = Arrays.asList(0.0, 0.0, 0.35, -0.35, 0.0);
            modelData.setWeights(weights);
        }
        double bias = modelData.getBias() == null ? 0.0 : modelData.getBias();
        double loss = 0.0;
        int correct = 0;

        for (DataPoint2d point : data.points) {
            double target = point.classValue > 0.5 ? 1.0 : -1.0;
            double score = "rbf".equals(kernel)
                    ? scoreRbf(stats, weights, bias, gamma, point.x, point.y)
                    : scorePoly(
                            getWeight(modelData, 0),
                            getWeight(modelData, 1),
                            getWeight(modelData, 2),
                            getWeight(modelData, 3),
                            getWeight(modelData, 4),
                            bias,
                            gamma,
                            stats.normalizeX(point.x),
                            stats.normalizeY(point.y));
            loss += Math.max(0.0, 1.0 - target * score);
            int predicted = score >= 0.0 ? 1 : 0;
            if (predicted == (int) point.classValue) {
                correct += 1;
            }
        }

        if ("rbf".equals(kernel)) {
            List<Double> finalWeights = weights;
            modelData.setDecisionBoundary(buildSampledBoundary(stats, (x, y) -> scoreRbf(stats, finalWeights, bias, gamma, x, y)));
        } else {
            modelData.setDecisionBoundary(buildSampledBoundary(stats, (rawX, rawY) -> scorePoly(
                    getWeight(modelData, 0),
                    getWeight(modelData, 1),
                    getWeight(modelData, 2),
                    getWeight(modelData, 3),
                    getWeight(modelData, 4),
                    bias,
                    gamma,
                    stats.normalizeX(rawX),
                    stats.normalizeY(rawY))));
        }
        double accuracy = data.points.isEmpty() ? 0.0 : (double) correct / data.points.size();
        return new StepResult(modelData, round(loss / Math.max(1, data.points.size())), round(accuracy), "Accuracy");
    }

    private StepResult evaluateLinearSvm(Dataset dataset, TrainingSimulationResponse.ModelData modelData) {
        DataSet2d data = loadDataSet(dataset, null);
        DataStats stats = new DataStats(data.points);
        double w1 = getWeight(modelData, 0);
        double w2 = getWeight(modelData, 1);
        double b = modelData.getBias() == null ? 0.0 : modelData.getBias();
        double loss = 0.0;
        int correct = 0;

        for (DataPoint2d point : data.points) {
            double x = stats.normalizeX(point.x);
            double y = stats.normalizeY(point.y);
            double target = point.classValue > 0.5 ? 1.0 : -1.0;
            double score = w1 * x + w2 * y + b;
            loss += Math.max(0.0, 1.0 - target * score);
            int predicted = score >= 0.0 ? 1 : 0;
            if (predicted == (int) point.classValue) {
                correct += 1;
            }
        }

        modelData.setDecisionBoundary(buildLinearBoundary(stats, List.of(w1, w2), b));
        double regularization = 0.5 * (w1 * w1 + w2 * w2);
        double accuracy = data.points.isEmpty() ? 0.0 : (double) correct / data.points.size();
        return new StepResult(modelData, round((loss / Math.max(1, data.points.size())) + regularization), round(accuracy), "Accuracy");
    }

    private DataSet2d loadDataSet(Dataset dataset, Algorithm algorithm) {
        List<DataPoint2d> points = new ArrayList<>();
        List<FeatureInfo> features = parseFeatures(dataset.getFeatures());
        String labelName = features.stream()
                .filter(feature -> "label".equals(feature.type))
                .map(feature -> feature.name)
                .findFirst()
                .orElse(null);
        String preferredX = preferredFeature(features, List.of("rm", "x", "petal_length", "sepal_length"));
        String preferredY = preferredFeature(features, List.of("y", "petal_width", "sepal_width"));
        boolean useTargetAsY = preferredY == null && labelName != null;
        Map<String, Integer> labelMap = new HashMap<>();

        for (LinkedHashMap<String, Object> row : parseRows(dataset.getDataContent())) {
            Double target = labelName == null ? null : asDouble(row.get(labelName));
            Object rawLabel = labelName == null ? row.get("label") : row.get(labelName);
            int classIndex = toClassIndex(rawLabel, labelMap);
            double classValue = classIndex == 0 ? 0.0 : 1.0;
            Double x = preferredX == null ? null : asDouble(row.get(preferredX));
            Double y = preferredY == null ? null : asDouble(row.get(preferredY));
            if (y == null && useTargetAsY) {
                y = target;
            }

            if (x == null || y == null) {
                List<Double> numericValues = row.entrySet().stream()
                        .filter(entry -> labelName == null || !labelName.equals(entry.getKey()))
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

            if (target == null) {
                target = y;
            }
            if (x != null && y != null && target != null) {
                points.add(new DataPoint2d(x, y, target, classValue, classIndex));
            }
        }

        if (points.isEmpty()) {
            points.add(new DataPoint2d(0.0, 0.0, 0.0, 0.0, 0));
            points.add(new DataPoint2d(1.0, 1.0, 1.0, 1.0, 1));
        }
        return new DataSet2d(points);
    }

    private String preferredFeature(List<FeatureInfo> features, List<String> preferredNames) {
        for (String preferredName : preferredNames) {
            String matched = features.stream()
                    .filter(feature -> !"label".equals(feature.type))
                    .map(feature -> feature.name)
                    .filter(preferredName::equals)
                    .findFirst()
                    .orElse(null);
            if (matched != null) {
                return matched;
            }
        }
        return null;
    }

    private List<FeatureInfo> parseFeatures(String featuresJson) {
        if (featuresJson == null || featuresJson.isBlank()) {
            return List.of();
        }
        try {
            List<LinkedHashMap<String, Object>> rows = objectMapper.readValue(
                    featuresJson,
                    new TypeReference<List<LinkedHashMap<String, Object>>>() {});
            return rows.stream()
                    .map(row -> new FeatureInfo(String.valueOf(row.get("name")), String.valueOf(row.get("type"))))
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<LinkedHashMap<String, Object>> parseRows(String dataContent) {
        if (dataContent == null || dataContent.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(dataContent, new TypeReference<List<LinkedHashMap<String, Object>>>() {});
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<TrainingSimulationResponse.Centroid> initializeCentroids(List<DataPoint2d> points, int k) {
        DataStats stats = new DataStats(points);
        List<TrainingSimulationResponse.Centroid> centroids = new ArrayList<>();
        for (int index = 0; index < k; index++) {
            double x = stats.minX + stats.rangeX() * (index + 1) / (k + 1);
            double y = stats.minY + stats.rangeY() * ((index % 2) + 1) / 3.0;
            TrainingSimulationResponse.Centroid centroid = new TrainingSimulationResponse.Centroid();
            centroid.setX(round(x));
            centroid.setY(round(y));
            centroid.setClusterId(index);
            centroids.add(centroid);
        }
        return centroids;
    }

    private List<Integer> assignClusters(List<DataPoint2d> points, List<TrainingSimulationResponse.Centroid> centroids) {
        List<Integer> assignments = new ArrayList<>();
        for (DataPoint2d point : points) {
            int bestIndex = 0;
            double bestDistance = Double.MAX_VALUE;
            for (int index = 0; index < centroids.size(); index++) {
                TrainingSimulationResponse.Centroid centroid = centroids.get(index);
                double distance = squaredDistance(point.x, point.y, centroid.getX(), centroid.getY());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestIndex = index;
                }
            }
            assignments.add(bestIndex);
        }
        return assignments;
    }

    private List<TrainingSimulationResponse.Centroid> recomputeCentroids(
            List<DataPoint2d> points,
            List<Integer> assignments,
            List<TrainingSimulationResponse.Centroid> previous) {
        double momentum = 0.35;
        List<TrainingSimulationResponse.Centroid> updated = new ArrayList<>();
        for (int clusterId = 0; clusterId < previous.size(); clusterId++) {
            double sumX = 0.0;
            double sumY = 0.0;
            int count = 0;
            for (int index = 0; index < points.size(); index++) {
                if (assignments.get(index) == clusterId) {
                    sumX += points.get(index).x;
                    sumY += points.get(index).y;
                    count += 1;
                }
            }
            TrainingSimulationResponse.Centroid centroid = new TrainingSimulationResponse.Centroid();
            centroid.setClusterId(clusterId);
            double prevX = previous.get(clusterId).getX();
            double prevY = previous.get(clusterId).getY();
            if (count == 0) {
                centroid.setX(prevX);
                centroid.setY(prevY);
            } else {
                centroid.setX(round(prevX + (sumX / count - prevX) * momentum));
                centroid.setY(round(prevY + (sumY / count - prevY) * momentum));
            }
            updated.add(centroid);
        }
        return updated;
    }

    private DecisionSplit findBestDecisionSplit(List<DataPoint2d> points, DecisionTreeConfig config) {
        DecisionSplit best = null;
        for (String feature : List.of("x", "y")) {
            List<Double> values = points.stream()
                    .map(point -> "x".equals(feature) ? point.x : point.y)
                    .distinct()
                    .sorted()
                    .toList();
            for (int index = 0; index < values.size() - 1; index++) {
                double threshold = (values.get(index) + values.get(index + 1)) / 2.0;
                if (!canSplit(points, feature, threshold, config.minSamplesSplit)) {
                    continue;
                }
                DecisionSplit candidate = buildDecisionSplit(points, feature, threshold, config);
                double projectedImpurity = projectedTreeImpurity(points, feature, threshold, config, 1);
                candidate = new DecisionSplit(
                        candidate.feature,
                        candidate.threshold,
                        projectedImpurity,
                        candidate.leftClassIndex,
                        candidate.rightClassIndex);
                if (best == null || candidate.impurity < best.impurity) {
                    best = candidate;
                }
            }
        }
        if (best != null) {
            return best;
        }
        return new DecisionSplit("x", points.get(0).x, nodeImpurity(points, config), majorityClass(points), majorityClass(points));
    }

    private DecisionSplit buildDecisionSplit(List<DataPoint2d> points, String feature, double threshold, DecisionTreeConfig config) {
        Map<Integer, Integer> leftCounts = new HashMap<>();
        Map<Integer, Integer> rightCounts = new HashMap<>();
        int leftCount = 0;
        int rightCount = 0;
        for (DataPoint2d point : points) {
            boolean left = ("x".equals(feature) ? point.x : point.y) <= threshold;
            if (left) {
                leftCount += 1;
                leftCounts.merge(point.classIndex, 1, Integer::sum);
            } else {
                rightCount += 1;
                rightCounts.merge(point.classIndex, 1, Integer::sum);
            }
        }
        double impurity = ((double) leftCount / points.size()) * nodeImpurity(leftCounts, leftCount, config)
                + ((double) rightCount / points.size()) * nodeImpurity(rightCounts, rightCount, config);
        return new DecisionSplit(
                feature,
                threshold,
                impurity,
                majorityClass(leftCounts),
                majorityClass(rightCounts));
    }

    private boolean canSplit(List<DataPoint2d> points, String feature, double threshold, int minSamplesSplit) {
        if (points.size() < minSamplesSplit) {
            return false;
        }
        int leftCount = 0;
        int rightCount = 0;
        for (DataPoint2d point : points) {
            if (isLeft(point, feature, threshold)) {
                leftCount += 1;
            } else {
                rightCount += 1;
            }
        }
        return leftCount > 0 && rightCount > 0;
    }

    private double projectedTreeImpurity(
            List<DataPoint2d> points,
            String feature,
            double threshold,
            DecisionTreeConfig config,
            int depth) {
        List<DataPoint2d> left = new ArrayList<>();
        List<DataPoint2d> right = new ArrayList<>();
        for (DataPoint2d point : points) {
            if (isLeft(point, feature, threshold)) {
                left.add(point);
            } else {
                right.add(point);
            }
        }
        return ((double) left.size() / points.size()) * bestSubtreeImpurity(left, config, depth + 1)
                + ((double) right.size() / points.size()) * bestSubtreeImpurity(right, config, depth + 1);
    }

    private double bestSubtreeImpurity(List<DataPoint2d> points, DecisionTreeConfig config, int depth) {
        if (points.isEmpty() || depth > config.maxDepth || points.size() < config.minSamplesSplit) {
            return nodeImpurity(points, config);
        }

        double best = nodeImpurity(points, config);
        for (String feature : List.of("x", "y")) {
            List<Double> values = points.stream()
                    .map(point -> "x".equals(feature) ? point.x : point.y)
                    .distinct()
                    .sorted()
                    .toList();
            for (int index = 0; index < values.size() - 1; index++) {
                double threshold = (values.get(index) + values.get(index + 1)) / 2.0;
                if (canSplit(points, feature, threshold, config.minSamplesSplit)) {
                    best = Math.min(best, projectedTreeImpurity(points, feature, threshold, config, depth));
                }
            }
        }
        return best;
    }

    private boolean isLeft(DataPoint2d point, String feature, double threshold) {
        return ("x".equals(feature) ? point.x : point.y) <= threshold;
    }

    private double nodeImpurity(List<DataPoint2d> points, DecisionTreeConfig config) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (DataPoint2d point : points) {
            counts.merge(point.classIndex, 1, Integer::sum);
        }
        return nodeImpurity(counts, points.size(), config);
    }

    private double nodeImpurity(Map<Integer, Integer> counts, int count, DecisionTreeConfig config) {
        if ("entropy".equals(config.criterion)) {
            return entropy(counts, count);
        }
        return gini(counts, count);
    }

    private double gini(Map<Integer, Integer> counts, int count) {
        if (count == 0) {
            return 0.0;
        }
        double impurity = 1.0;
        for (Integer classCount : counts.values()) {
            double p = (double) classCount / count;
            impurity -= p * p;
        }
        return impurity;
    }

    private double entropy(Map<Integer, Integer> counts, int count) {
        if (count == 0) {
            return 0.0;
        }
        double impurity = 0.0;
        for (Integer classCount : counts.values()) {
            double p = (double) classCount / count;
            impurity -= p * (Math.log(p) / Math.log(2.0));
        }
        return impurity;
    }

    private int majorityClass(List<DataPoint2d> points) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (DataPoint2d point : points) {
            counts.merge(point.classIndex, 1, Integer::sum);
        }
        return majorityClass(counts);
    }

    private int majorityClass(Map<Integer, Integer> counts) {
        return counts.entrySet().stream()
                .max((left, right) -> Integer.compare(left.getValue(), right.getValue()))
                .map(Map.Entry::getKey)
                .orElse(0);
    }

    private int predictDecisionSplit(DataPoint2d point, DecisionSplit split) {
        boolean left = isLeft(point, split.feature, split.threshold);
        return left ? split.leftClassIndex : split.rightClassIndex;
    }

    private void applyDecisionSplitModel(
            TrainingSimulationResponse.ModelData modelData,
            DataStats stats,
            DecisionSplit split) {
        modelData.setSplitFeature(split.feature);
        modelData.setSplitThreshold(round(split.threshold));
        modelData.setSplitImpurity(round(split.impurity));
        if ("x".equals(split.feature)) {
            modelData.setDecisionBoundary(List.of(
                    Arrays.asList(round(split.threshold), round(stats.minY)),
                    Arrays.asList(round(split.threshold), round(stats.maxY))));
        } else {
            modelData.setDecisionBoundary(List.of(
                    Arrays.asList(round(stats.minX), round(split.threshold)),
                    Arrays.asList(round(stats.maxX), round(split.threshold))));
        }
    }

    private List<List<Double>> buildRegressionLine(DataStats stats, double normalizedWeight, double normalizedBias) {
        double y1 = stats.denormalizeTarget(normalizedWeight * stats.normalizeX(stats.minX) + normalizedBias);
        double y2 = stats.denormalizeTarget(normalizedWeight * stats.normalizeX(stats.maxX) + normalizedBias);
        return Arrays.asList(Arrays.asList(round(stats.minX), round(y1)), Arrays.asList(round(stats.maxX), round(y2)));
    }

    private List<List<Double>> buildLinearBoundary(DataStats stats, List<Double> weights, double bias) {
        double w1 = weights.isEmpty() ? 0.0 : weights.get(0);
        double w2 = weights.size() < 2 ? 0.0 : weights.get(1);
        double x1 = stats.minX;
        double x2 = stats.maxX;
        if (Math.abs(w2) < EPSILON) {
            double boundaryX = Math.abs(w1) < EPSILON ? (stats.minX + stats.maxX) / 2.0 : stats.denormalizeX(-bias / w1);
            return Arrays.asList(Arrays.asList(round(boundaryX), round(stats.minY)), Arrays.asList(round(boundaryX), round(stats.maxY)));
        }
        double y1 = stats.denormalizeY((-bias - w1 * stats.normalizeX(x1)) / w2);
        double y2 = stats.denormalizeY((-bias - w1 * stats.normalizeX(x2)) / w2);
        return Arrays.asList(Arrays.asList(round(x1), round(y1)), Arrays.asList(round(x2), round(y2)));
    }

    private List<List<Double>> buildSampledBoundary(DataStats stats, BoundaryScorer scorer) {
        List<List<Double>> boundary = new ArrayList<>();
        int xSteps = 36;
        int ySteps = 72;
        for (int xi = 0; xi <= xSteps; xi++) {
            double rawX = stats.minX + stats.rangeX() * xi / xSteps;
            double previousY = stats.minY;
            double previousScore = scorer.score(rawX, previousY);
            double bestY = previousY;
            double bestAbsScore = Math.abs(previousScore);
            boolean foundCrossing = false;
            for (int yi = 1; yi <= ySteps; yi++) {
                double rawY = stats.minY + stats.rangeY() * yi / ySteps;
                double score = scorer.score(rawX, rawY);
                double absScore = Math.abs(score);
                if (absScore < bestAbsScore) {
                    bestAbsScore = absScore;
                    bestY = rawY;
                }
                if (previousScore == 0.0 || score == 0.0 || Math.signum(previousScore) != Math.signum(score)) {
                    double ratio = Math.abs(previousScore) / Math.max(EPSILON, Math.abs(previousScore) + Math.abs(score));
                    double crossingY = previousY + (rawY - previousY) * ratio;
                    boundary.add(Arrays.asList(round(rawX), round(crossingY)));
                    foundCrossing = true;
                    break;
                }
                previousY = rawY;
                previousScore = score;
            }
            if (!foundCrossing && bestAbsScore < 0.35) {
                boundary.add(Arrays.asList(round(rawX), round(bestY)));
            }
        }
        if (boundary.size() >= 2) {
            return boundary;
        }
        return Arrays.asList(
                Arrays.asList(round(stats.minX), round(stats.minY + stats.rangeY() / 2.0)),
                Arrays.asList(round(stats.maxX), round(stats.minY + stats.rangeY() / 2.0)));
    }

    private List<Double> classCenters(List<DataPoint2d> points, DataStats stats) {
        double negX = 0.0;
        double negY = 0.0;
        double posX = 0.0;
        double posY = 0.0;
        int negCount = 0;
        int posCount = 0;
        for (DataPoint2d point : points) {
            if (point.classValue > 0.5) {
                posX += stats.normalizeX(point.x);
                posY += stats.normalizeY(point.y);
                posCount += 1;
            } else {
                negX += stats.normalizeX(point.x);
                negY += stats.normalizeY(point.y);
                negCount += 1;
            }
        }
        if (negCount == 0) {
            negCount = 1;
            negX = -0.5;
            negY = -0.5;
        }
        if (posCount == 0) {
            posCount = 1;
            posX = 0.5;
            posY = 0.5;
        }
        return Arrays.asList(negX / negCount, negY / negCount, posX / posCount, posY / posCount);
    }

    private double blendWeight(List<Double> weights, int index, double target, double momentum) {
        double current = weights == null || weights.size() <= index ? 0.0 : weights.get(index);
        return current + (target - current) * momentum;
    }

    private double scoreRbf(DataStats stats, List<Double> weights, double bias, double gamma, double rawX, double rawY) {
        double x = stats.normalizeX(rawX);
        double y = stats.normalizeY(rawY);
        double negDistance = squaredDistance(x, y, weights.get(0), weights.get(1));
        double posDistance = squaredDistance(x, y, weights.get(2), weights.get(3));
        return Math.exp(-gamma * posDistance) - Math.exp(-gamma * negDistance) + bias;
    }

    private double scorePoly(
            double w1,
            double w2,
            double q1,
            double q2,
            double q3,
            double bias,
            double gamma,
            double x,
            double y) {
        return w1 * x + w2 * y + bias + gamma * (q1 * x * x + q2 * y * y + q3 * x * y);
    }

    private double approximateSilhouette(
            List<DataPoint2d> points,
            List<Integer> assignments,
            List<TrainingSimulationResponse.Centroid> centroids) {
        if (centroids.size() <= 1 || points.isEmpty()) {
            return 0.0;
        }
        double total = 0.0;
        for (int index = 0; index < points.size(); index++) {
            DataPoint2d point = points.get(index);
            int ownCluster = assignments.get(index);
            TrainingSimulationResponse.Centroid own = centroids.get(ownCluster);
            double a = Math.sqrt(squaredDistance(point.x, point.y, own.getX(), own.getY()));
            double b = Double.MAX_VALUE;
            for (int clusterId = 0; clusterId < centroids.size(); clusterId++) {
                if (clusterId == ownCluster) {
                    continue;
                }
                TrainingSimulationResponse.Centroid other = centroids.get(clusterId);
                b = Math.min(b, Math.sqrt(squaredDistance(point.x, point.y, other.getX(), other.getY())));
            }
            total += (b - a) / Math.max(a, b);
        }
        return clamp((total / points.size() + 1.0) / 2.0, 0.0, 0.99);
    }

    private double normalizeInertia(double inertia, List<DataPoint2d> points) {
        DataStats stats = new DataStats(points);
        double scale = Math.max(EPSILON, points.size() * (stats.rangeX() * stats.rangeX() + stats.rangeY() * stats.rangeY()));
        return inertia / scale;
    }

    private double squaredDistance(double x1, double y1, double x2, double y2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        return dx * dx + dy * dy;
    }

    private int toClassIndex(Object rawLabel, Map<String, Integer> labelMap) {
        Double numeric = asDouble(rawLabel);
        if (numeric != null) {
            return (int) Math.round(numeric);
        }
        String key = rawLabel == null ? "0" : String.valueOf(rawLabel);
        return labelMap.computeIfAbsent(key, ignored -> labelMap.size());
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

    private int getK(String parameters) {
        return clampInt(getNumericParameter(parameters, "k", 3), 2, 6);
    }

    private DecisionTreeConfig getDecisionTreeConfig(String parameters) {
        int maxDepth = clampInt(getNumericParameter(parameters, "maxDepth", 5), 1, 20);
        int minSamplesSplit = clampInt(getNumericParameter(parameters, "minSamplesSplit", 2), 2, 50);
        String criterion = getStringParameter(parameters, "criterion", "gini");
        if (!"entropy".equals(criterion)) {
            criterion = "gini";
        }
        return new DecisionTreeConfig(maxDepth, minSamplesSplit, criterion);
    }

    private String getSvmKernel(String parameters) {
        String kernel = getStringParameter(parameters, "kernel", "rbf");
        if ("rbf".equals(kernel) || "poly".equals(kernel)) {
            return kernel;
        }
        return "linear";
    }

    private double getSvmGamma(String parameters) {
        return clamp(getNumericParameter(parameters, "gamma", 0.1), 0.001, 1.0);
    }

    private double getWeight(TrainingSimulationResponse.ModelData modelData, int index) {
        List<Double> weights = modelData.getWeights();
        if (weights == null || weights.size() <= index || weights.get(index) == null) {
            return 0.0;
        }
        return weights.get(index);
    }

    private double getNumericParameter(String parameters, String name, double defaultValue) {
        if (parameters == null || parameters.isBlank()) {
            return defaultValue;
        }
        try {
            Map<String, Object> values = objectMapper.readValue(parameters, new TypeReference<Map<String, Object>>() {});
            Object value = values.get(name);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
        } catch (Exception ignored) {
            return defaultValue;
        }
        return defaultValue;
    }

    private String getStringParameter(String parameters, String name, String defaultValue) {
        if (parameters == null || parameters.isBlank()) {
            return defaultValue;
        }
        try {
            Map<String, Object> values = objectMapper.readValue(parameters, new TypeReference<Map<String, Object>>() {});
            Object value = values.get(name);
            if (value instanceof String text && !text.isBlank()) {
                return text;
            }
        } catch (Exception ignored) {
            return defaultValue;
        }
        return defaultValue;
    }

    private String normalizeParameters(String parameters) {
        return parameters == null || parameters.isBlank() ? "{}" : parameters;
    }

    private int getTotalStepsFromParameters(String parameters, Algorithm algorithm) {
        // Try to get iterations or maxIterations from parameters
        int iterations = (int) getNumericParameter(parameters, "iterations", -1);
        if (iterations <= 0) {
            iterations = (int) getNumericParameter(parameters, "maxIterations", -1);
        }
        if (iterations > 0) {
            return clampInt(iterations, 10, 1000);
        }
        // For decision tree, use maxDepth * 2
        if (algorithm != null && "decision_tree".equals(algorithm.getName())) {
            int maxDepth = clampInt(getNumericParameter(parameters, "maxDepth", 5), 1, 20);
            return maxDepth * 2;
        }
        return DEFAULT_TOTAL_STEPS;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize training state");
        }
    }

    private <T> T fromJson(String value, Class<T> valueType) {
        try {
            return objectMapper.readValue(value, valueType);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse training state");
        }
    }

    private double interpolateLoss(double currentLoss, double progress) {
        return round(Math.max(currentLoss, currentLoss + (1.0 - progress) * 0.72));
    }

    private double interpolateScore(double currentScore, double progress) {
        return round(Math.min(currentScore, currentScore * (0.38 + progress * 0.62)));
    }

    private String getLossLabel(Algorithm algorithm) {
        String name = algorithm.getName() == null ? "" : algorithm.getName();
        if ("kmeans".equals(name)) {
            return "Inertia";
        }
        if ("linear_regression".equals(name)) {
            return "MSE";
        }
        if ("svm".equals(name)) {
            return "Hinge Loss";
        }
        if ("decision_tree".equals(name)) {
            return "Gini";
        }
        return "Loss";
    }

    private double sigmoid(double value) {
        if (value >= 0.0) {
            double z = Math.exp(-value);
            return 1.0 / (1.0 + z);
        }
        double z = Math.exp(value);
        return z / (1.0 + z);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private int clampInt(double value, int min, int max) {
        return Math.max(min, Math.min(max, (int) Math.round(value)));
    }

    private Algorithm requireAlgorithm(Long algorithmId) {
        Algorithm algorithm = algorithmRepository.findById(algorithmId);
        if (algorithm == null) {
            throw new IllegalArgumentException("Algorithm not found with id: " + algorithmId);
        }
        return algorithm;
    }

    // Build numeric matrix (rows x features) from parsed rows and feature names
    private double[][] buildNumericMatrix(List<LinkedHashMap<String, Object>> rows, List<String> numericNames) {
        if (rows == null || rows.isEmpty() || numericNames == null || numericNames.isEmpty()) {
            return new double[0][0];
        }
        List<double[]> matrix = new ArrayList<>();
        for (LinkedHashMap<String, Object> row : rows) {
            double[] r = new double[numericNames.size()];
            boolean ok = true;
            for (int j = 0; j < numericNames.size(); j++) {
                Object val = row.get(numericNames.get(j));
                Double d = asDouble(val);
                if (d == null) { ok = false; break; }
                r[j] = d;
            }
            if (ok) matrix.add(r);
        }
        double[][] out = new double[matrix.size()][];
        for (int i = 0; i < matrix.size(); i++) out[i] = matrix.get(i);
        return out;
    }

    private static class PcaResult {
        double[][] components;
        double[][] transformed;
        double[] eigenvalues;
    }

    // Compute top-k PCA components using power iteration on covariance matrix
    private PcaResult computePca(double[][] matrix, int k) {
        int n = matrix.length;
        int m = matrix[0].length;
        // center columns
        double[] mean = new double[m];
        for (int j = 0; j < m; j++) {
            double s = 0.0;
            for (int i = 0; i < n; i++) s += matrix[i][j];
            mean[j] = s / Math.max(1, n);
        }
        double[][] centered = new double[n][m];
        for (int i = 0; i < n; i++) for (int j = 0; j < m; j++) centered[i][j] = matrix[i][j] - mean[j];

        // covariance matrix (m x m)
        double[][] cov = new double[m][m];
        for (int i = 0; i < m; i++) {
            for (int j = i; j < m; j++) {
                double s = 0.0;
                for (int r = 0; r < n; r++) s += centered[r][i] * centered[r][j];
                double v = n > 1 ? s / (n - 1) : 0.0;
                cov[i][j] = v;
                cov[j][i] = v;
            }
        }

        double[][] components = new double[k][m];
        double[] eigenvalues = new double[k];
        double[][] covWorking = new double[m][m];
        for (int i = 0; i < m; i++) System.arraycopy(cov[i], 0, covWorking[i], 0, m);

        for (int comp = 0; comp < k; comp++) {
            double[] v = new double[m];
            // init random
            for (int i = 0; i < m; i++) v[i] = Math.random() - 0.5;
            // power iteration
            for (int it = 0; it < 60; it++) {
                double[] next = new double[m];
                for (int i = 0; i < m; i++) {
                    double s = 0.0;
                    for (int j = 0; j < m; j++) s += covWorking[i][j] * v[j];
                    next[i] = s;
                }
                double norm = 0.0; for (int i = 0; i < m; i++) norm += next[i] * next[i];
                norm = Math.sqrt(Math.max(1e-12, norm));
                for (int i = 0; i < m; i++) v[i] = next[i] / norm;
            }
            // eigenvalue
            double[] Cv = new double[m];
            for (int i = 0; i < m; i++) {
                double s = 0.0; for (int j = 0; j < m; j++) s += covWorking[i][j] * v[j];
                Cv[i] = s;
            }
            double lambda = 0.0; for (int i = 0; i < m; i++) lambda += v[i] * Cv[i];
            eigenvalues[comp] = lambda;
            // store component
            for (int i = 0; i < m; i++) components[comp][i] = v[i];
            // deflate
            for (int i = 0; i < m; i++) for (int j = 0; j < m; j++) covWorking[i][j] -= lambda * v[i] * v[j];
        }

        // project data
        double[][] transformed = new double[n][k];
        for (int i = 0; i < n; i++) {
            for (int comp = 0; comp < k; comp++) {
                double s = 0.0;
                for (int j = 0; j < m; j++) s += centered[i][j] * components[comp][j];
                transformed[i][comp] = s;
            }
        }

        PcaResult res = new PcaResult();
        res.components = components;
        res.transformed = transformed;
        res.eigenvalues = eigenvalues;
        return res;
    }

    private String buildFeaturesJson(List<String> selectedFeatures, Dataset dataset) {
        List<FeatureInfo> original = parseFeatures(dataset.getFeatures());
        List<Map<String, Object>> out = new ArrayList<>();
        for (FeatureInfo f : original) {
            if (selectedFeatures.contains(f.name())) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", f.name());
                m.put("type", f.type());
                out.add(m);
            }
        }
        try {
            return objectMapper.writeValueAsString(out);
        } catch (Exception e) {
            return "[]";
        }
    }

    private Dataset requireDataset(Long datasetId) {
        Dataset dataset = datasetRepository.findById(datasetId);
        if (dataset == null) {
            throw new IllegalArgumentException("Dataset not found with id: " + datasetId);
        }
        return dataset;
    }

    private TrainingSession requireSession(Long sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId is required");
        }
        TrainingSession session = trainingSessionRepository.findById(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Training session not found with id: " + sessionId);
        }
        return session;
    }

    private void validateSession(TrainingSession session, Long algorithmId, Long datasetId) {
        if (!session.getAlgorithmId().equals(algorithmId)) {
            throw new IllegalArgumentException("Training session does not belong to algorithm: " + algorithmId);
        }
        if (datasetId != null && !session.getDatasetId().equals(datasetId)) {
            throw new IllegalArgumentException("Training session does not belong to dataset: " + datasetId);
        }
    }

    private record FeatureInfo(String name, String type) {}

    private record DataPoint2d(double x, double y, double target, double classValue, int classIndex) {}

    private record DataSet2d(List<DataPoint2d> points) {}

    private record DecisionSplit(String feature, double threshold, double impurity, int leftClassIndex, int rightClassIndex) {}

    private record DecisionTreeConfig(int maxDepth, int minSamplesSplit, String criterion) {}

    @FunctionalInterface
    private interface BoundaryScorer {
        double score(double x, double y);
    }

    private record StepResult(
            TrainingSimulationResponse.ModelData modelData,
            double loss,
            double score,
            String scoreLabel) {}

    private static class DataStats {
        private final double minX;
        private final double maxX;
        private final double minY;
        private final double maxY;
        private final double avgTarget;
        private final double stdX;
        private final double stdY;
        private final double stdTarget;

        private DataStats(List<DataPoint2d> points) {
            this.minX = points.stream().mapToDouble(point -> point.x).min().orElse(0.0);
            this.maxX = points.stream().mapToDouble(point -> point.x).max().orElse(1.0);
            this.minY = points.stream().mapToDouble(point -> point.y).min().orElse(0.0);
            this.maxY = points.stream().mapToDouble(point -> point.y).max().orElse(1.0);
            double avgX = points.stream().mapToDouble(point -> point.x).average().orElse(0.0);
            double avgY = points.stream().mapToDouble(point -> point.y).average().orElse(0.0);
            this.avgTarget = points.stream().mapToDouble(point -> point.target).average().orElse(0.0);
            this.stdX = std(points.stream().mapToDouble(point -> point.x).toArray(), avgX);
            this.stdY = std(points.stream().mapToDouble(point -> point.y).toArray(), avgY);
            this.stdTarget = std(points.stream().mapToDouble(point -> point.target).toArray(), avgTarget);
        }

        private double rangeX() {
            return Math.max(EPSILON, maxX - minX);
        }

        private double rangeY() {
            return Math.max(EPSILON, maxY - minY);
        }

        private double normalizeX(double value) {
            return (value - (minX + maxX) / 2.0) / Math.max(EPSILON, rangeX() / 2.0);
        }

        private double normalizeY(double value) {
            return (value - (minY + maxY) / 2.0) / Math.max(EPSILON, rangeY() / 2.0);
        }

        private double normalizeTarget(double value) {
            return (value - avgTarget) / Math.max(EPSILON, stdTarget);
        }

        private double denormalizeTarget(double value) {
            return value * Math.max(EPSILON, stdTarget) + avgTarget;
        }

        private double denormalizeX(double value) {
            return value * Math.max(EPSILON, rangeX() / 2.0) + (minX + maxX) / 2.0;
        }

        private double denormalizeY(double value) {
            return value * Math.max(EPSILON, rangeY() / 2.0) + (minY + maxY) / 2.0;
        }

        private static double std(double[] values, double average) {
            if (values.length == 0) {
                return 1.0;
            }
            double variance = 0.0;
            for (double value : values) {
                double diff = value - average;
                variance += diff * diff;
            }
            return Math.max(EPSILON, Math.sqrt(variance / values.length));
        }
    }
}
