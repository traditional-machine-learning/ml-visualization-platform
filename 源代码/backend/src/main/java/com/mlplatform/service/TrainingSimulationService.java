package com.mlplatform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mlplatform.dto.ProjectionDTO;
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

    /**
     * 把当前 session 的模型投影到用户所选的两个维度上。
     * <p>
     * 线性回归 / 逻辑回归 / K-Means 是真正的全维度模型，这里按平面切片重新求几何；
     * 决策树 / SVM / 随机森林仍按两维训练，只有在请求的两维等于训练维度时才回放过往结果。
     */
    public ProjectionDTO projectModel(Long sessionId, String xName, String yName) {
        TrainingSession session = requireSession(sessionId);
        Algorithm algorithm = requireAlgorithm(session.getAlgorithmId());
        Dataset dataset = requireDataset(session.getDatasetId());
        TrainingSimulationResponse.ModelData modelData = readOrInitializeModelData(session, algorithm, dataset);
        return project(algorithm, dataset, modelData, sessionId, xName, yName);
    }

    private ProjectionDTO project(
            Algorithm algorithm,
            Dataset dataset,
            TrainingSimulationResponse.ModelData modelData,
            Long sessionId,
            String xName,
            String yName) {
        ProjectionDTO projection = new ProjectionDTO();
        projection.setSessionId(sessionId);
        projection.setXName(xName);
        projection.setYName(yName);
        String name = algorithm.getName() == null ? "" : algorithm.getName();
        String type = algorithm.getType() == null ? "" : algorithm.getType();

        // 与训练侧用同一个判定，保证"全维训练"和"能投影"永远一致
        if (!isFullDimensionAlgorithm(name, type)) {
            return projectLegacy(dataset, modelData, projection, name, xName, yName);
        }
        if ("kmeans".equals(name) || "clustering".equals(type)) {
            return projectCentroids(dataset, modelData, projection, xName, yName);
        }
        if ("linear_regression".equals(name) || "regression".equals(type)) {
            return projectRegression(dataset, modelData, projection, xName, yName);
        }
        return projectLinearBoundary(dataset, modelData, projection, xName, yName);
    }

    /** 回归：只有当目标维度出现在坐标轴上时才能画线，否则只给提示 */
    private ProjectionDTO projectRegression(
            Dataset dataset,
            TrainingSimulationResponse.ModelData modelData,
            ProjectionDTO projection,
            String xName,
            String yName) {
        DataMatrix data = loadDataMatrix(dataset, TargetMode.SUPERVISED);
        String targetName = data.targetName;
        boolean targetOnX = targetName != null && targetName.equals(xName);
        boolean targetOnY = targetName != null && targetName.equals(yName);

        if (!targetOnX && !targetOnY) {
            String targetLabel = targetName == null ? "目标列" : targetName;
            return noProjection(
                    projection,
                    "目标维度 " + targetLabel + " 不在当前坐标轴上，无法投影回归线；"
                            + "请把 " + targetLabel + " 设为其中一条轴。");
        }

        String featureName = targetOnX ? yName : xName;
        int featureIndex = data.featureIndex(featureName);
        if (featureIndex < 0) {
            return noProjection(projection, "所选维度的取值无法用于回归投影。");
        }
        List<List<Double>> line = buildFullRegressionLine(
                data, modelData.getWeights(), modelData.getBias() == null ? 0.0 : modelData.getBias(), featureIndex);
        if (line == null) {
            return noProjection(projection, "当前模型还没有可投影的回归结果。");
        }
        projection.setKind(ProjectionDTO.KIND_REGRESSION_LINE);
        projection.setLine(targetOnX ? swapAxes(line) : line);
        projection.setNote(projectionNote(data, modelData, featureName, targetOnY ? yName : xName));
        return projection;
    }

    /** 线性分类：隐藏维固定在均值后仍是一条直线 */
    private ProjectionDTO projectLinearBoundary(
            Dataset dataset,
            TrainingSimulationResponse.ModelData modelData,
            ProjectionDTO projection,
            String xName,
            String yName) {
        DataMatrix data = loadDataMatrix(dataset, TargetMode.SUPERVISED);
        int xIndex = data.featureIndex(xName);
        int yIndex = data.featureIndex(yName);
        if (xIndex < 0 || yIndex < 0) {
            return noProjection(projection, "决策边界只能投影到两个特征维度上。");
        }
        List<List<Double>> line = buildFullLinearBoundary(
                data,
                xIndex,
                yIndex,
                modelData.getWeights(),
                modelData.getBias() == null ? 0.0 : modelData.getBias());
        if (line == null) {
            return noProjection(projection, "当前权重在这两个维度上无法确定边界（系数接近 0）。");
        }
        projection.setKind(ProjectionDTO.KIND_DECISION_BOUNDARY);
        projection.setLine(line);
        projection.setNote(projectionNote(data, modelData, xName, yName));
        return projection;
    }

    /** 聚类：质心直接取那两个维度的坐标；簇编号是全维聚类结果，换轴依然成立 */
    private ProjectionDTO projectCentroids(
            Dataset dataset,
            TrainingSimulationResponse.ModelData modelData,
            ProjectionDTO projection,
            String xName,
            String yName) {
        DataMatrix data = loadDataMatrix(dataset, TargetMode.NONE);
        int xIndex = data.featureIndex(xName);
        int yIndex = data.featureIndex(yName);
        if (xIndex < 0 || yIndex < 0) {
            return noProjection(projection, "聚类中心只能投影到两个特征维度上。");
        }
        List<TrainingSimulationResponse.Centroid> centroids = new ArrayList<>();
        if (modelData.getCentroids() != null) {
            for (TrainingSimulationResponse.Centroid centroid : modelData.getCentroids()) {
                List<Double> values = centroid.getValues();
                if (values == null || values.size() < data.featureCount()) {
                    continue;
                }
                TrainingSimulationResponse.Centroid projected = new TrainingSimulationResponse.Centroid();
                projected.setClusterId(centroid.getClusterId());
                projected.setX(round(values.get(xIndex)));
                projected.setY(round(values.get(yIndex)));
                projected.setValues(values);
                centroids.add(projected);
            }
        }
        projection.setKind(ProjectionDTO.KIND_CENTROIDS);
        projection.setCentroids(centroids);
        projection.setAssignments(modelData.getAssignments());
        projection.setNote("K-Means 在 " + data.featureCount() + " 维空间中聚类，"
                + "这里只取质心在当前两维上的坐标；点的颜色仍是全维聚类结果。");
        return projection;
    }

    /** 决策树 / SVM / 随机森林：本轮仍是两维训练，只有在训练维度上才有几何结果 */
    private ProjectionDTO projectLegacy(
            Dataset dataset,
            TrainingSimulationResponse.ModelData modelData,
            ProjectionDTO projection,
            String name,
            String xName,
            String yName) {
        List<String> axes = trainingAxes(dataset);
        String trainedX = axes.get(0);
        String trainedY = axes.get(1);
        String trainingLabel = trainedX + " / " + trainedY;
        String note = "该算法当前仍只在 " + trainingLabel + " 这两个维度上训练，"
                + "切换到其他坐标轴无法显示结果。";

        if (!trainedX.equals(xName) || !trainedY.equals(yName)) {
            return noProjection(projection, note);
        }
        if ("svm".equals(name) || "decision_tree".equals(name)) {
            List<List<Double>> boundary = modelData.getDecisionBoundary();
            List<List<Double>> regressionLine = modelData.getRegressionLine();
            List<List<Double>> line = boundary != null && boundary.size() >= 2 ? boundary : regressionLine;
            if (line == null || line.size() < 2) {
                return noProjection(projection, note);
            }
            projection.setKind(boundary != null && boundary.size() >= 2
                    ? ProjectionDTO.KIND_DECISION_BOUNDARY
                    : ProjectionDTO.KIND_REGRESSION_LINE);
            projection.setLine(line);
            projection.setNote(note);
            return projection;
        }
        return noProjection(projection, "该算法暂不提供平面投影结果。");
    }

    private ProjectionDTO noProjection(ProjectionDTO projection, String note) {
        projection.setKind(ProjectionDTO.KIND_NONE);
        projection.setLine(null);
        projection.setCentroids(null);
        projection.setAssignments(null);
        projection.setNote(note);
        return projection;
    }

    private List<List<Double>> swapAxes(List<List<Double>> line) {
        List<List<Double>> swapped = new ArrayList<>(line.size());
        for (List<Double> point : line) {
            swapped.add(Arrays.asList(point.get(1), point.get(0)));
        }
        return swapped;
    }

    private String projectionNote(
            DataMatrix data,
            TrainingSimulationResponse.ModelData modelData,
            String xName,
            String yName) {
        int hidden = Math.max(0, data.featureCount() - 2);
        String model = "logistic_regression".equals(modelData.getModelKind()) ? "逻辑回归" : "回归";
        return "全维度" + model + "（" + data.featureCount() + " 维特征）在 "
                + xName + " / " + yName + " 平面上的投影：其余 " + hidden + " 个维度固定在各自的均值。";
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
        String labelName = parseFeatures(dataset.getFeatures()).stream()
                .filter(feature -> "label".equals(feature.type))
                .map(feature -> feature.name)
                .findFirst()
                .orElse(null);
        // build filtered dataset：标签列必须一起保留，否则回归/分类找不到目标列
        List<LinkedHashMap<String, Object>> rows = parseRows(dataset.getDataContent());
        List<LinkedHashMap<String, Object>> filtered = new ArrayList<>();
        for (LinkedHashMap<String, Object> row : rows) {
            LinkedHashMap<String, Object> nr = new LinkedHashMap<>();
            for (String key : row.keySet()) {
                if (selectedFeatures.contains(key)
                        || "label".equals(key)
                        || (labelName != null && labelName.equals(key))) {
                    nr.put(key, row.get(key));
                }
            }
            filtered.add(nr);
        }
        Dataset tmp = new Dataset();
        tmp.setId(dataset.getId());
        tmp.setName(dataset.getName());
        tmp.setFeatures(buildFeaturesJson(selectedFeatures, dataset, labelName));
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
        String name = algorithm.getName() == null ? "" : algorithm.getName();
        String type = algorithm.getType() == null ? "" : algorithm.getType();

        if (isFullDimensionAlgorithm(name, type)) {
            return initializeFullDimensionModel(modelData, name, type, dataset, parameters);
        }

        DataSet2d data = loadDataSet(dataset, null);
        DataStats stats = new DataStats(data.points);

        if ("svm".equals(name)) {
            modelData.setWeights(Arrays.asList(0.0, 0.0));
            modelData.setBias(0.0);
            modelData.setDecisionBoundary(buildLinearBoundary(stats, List.of(0.0, 0.0), 0.0));
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

    /**
     * 本轮已完成全维度改造的算法：线性回归、逻辑回归、K-Means。
     * 决策树与 SVM 仍按 {@link #loadDataSet} 选出的两维训练，等后续单独升级。
     */
    private boolean isFullDimensionAlgorithm(String name, String type) {
        if ("svm".equals(name) || "decision_tree".equals(name) || "random_forest".equals(name)) {
            return false;
        }
        return "linear_regression".equals(name)
                || "logistic_regression".equals(name)
                || "kmeans".equals(name)
                || "regression".equals(type)
                || "classification".equals(type)
                || "clustering".equals(type);
    }

    private TrainingSimulationResponse.ModelData initializeFullDimensionModel(
            TrainingSimulationResponse.ModelData modelData,
            String name,
            String type,
            Dataset dataset,
            String parameters) {
        if ("kmeans".equals(name) || "clustering".equals(type)) {
            DataMatrix data = loadDataMatrix(dataset, TargetMode.NONE);
            applyMatrixMetadata(modelData, data, "kmeans");
            List<TrainingSimulationResponse.Centroid> centroids = initializeCentroids(data, getK(parameters));
            modelData.setCentroids(centroids);
            modelData.setAssignments(assignClusters(data, centroids));
            return modelData;
        }

        DataMatrix data = loadDataMatrix(dataset, TargetMode.SUPERVISED);
        boolean regression = "linear_regression".equals(name) || "regression".equals(type);
        applyMatrixMetadata(modelData, data, regression ? "linear_regression" : "logistic_regression");
        modelData.setWeights(zeroWeights(data.featureCount()));
        modelData.setBias(0.0);

        if (regression) {
            modelData.setRegressionLine(defaultRegressionLine(data, dataset, modelData.getWeights(), 0.0));
        } else {
            modelData.setDecisionBoundary(defaultBoundary(data, dataset, modelData.getWeights(), 0.0));
        }

        return modelData;
    }

    /** 全维度算法的模型自述：训练用了哪些维度、目标维度是谁 */
    private void applyMatrixMetadata(
            TrainingSimulationResponse.ModelData modelData,
            DataMatrix data,
            String modelKind) {
        modelData.setModelKind(modelKind);
        modelData.setFeatureNames(new ArrayList<>(data.featureNames));
        modelData.setTargetName(data.targetName);
    }

    private List<Double> zeroWeights(int size) {
        List<Double> weights = new ArrayList<>(Math.max(0, size));
        for (int index = 0; index < size; index++) {
            weights.add(0.0);
        }
        return weights;
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
        DataMatrix data = loadDataMatrix(dataset, TargetMode.SUPERVISED);
        applyMatrixMetadata(modelData, data, "linear_regression");
        int featureCount = data.featureCount();
        double[] weights = weightsArray(modelData, featureCount);
        double bias = modelData.getBias() == null ? 0.0 : modelData.getBias();
        double learningRate = clamp(getNumericParameter(parameters, "learningRate", 0.03), 0.0001, 0.2);
        double momentum = 0.35;

        double[] gradient = new double[featureCount];
        double gradientBias = 0.0;
        for (DataRow row : data.rows) {
            double prediction = dotNormalized(data, weights, row) + bias;
            double error = prediction - data.normalizeTarget(row.target);
            for (int index = 0; index < featureCount; index++) {
                gradient[index] += 2.0 * error * data.normalizeFeature(index, row.features[index]);
            }
            gradientBias += 2.0 * error;
        }
        int n = Math.max(1, data.rows.size());
        for (int index = 0; index < featureCount; index++) {
            double targetWeight = weights[index] - learningRate * gradient[index] / n;
            weights[index] = weights[index] + (targetWeight - weights[index]) * momentum;
        }
        double targetBias = bias - learningRate * gradientBias / n;
        bias = bias + (targetBias - bias) * momentum;

        modelData.setWeights(roundWeights(weights));
        modelData.setBias(round(bias));
        modelData.setRegressionLine(
                defaultRegressionLine(data, dataset, modelData.getWeights(), modelData.getBias()));
        return evaluateLinearRegression(dataset, modelData);
    }

    private StepResult evaluateLinearRegression(Dataset dataset, TrainingSimulationResponse.ModelData modelData) {
        DataMatrix data = loadDataMatrix(dataset, TargetMode.SUPERVISED);
        applyMatrixMetadata(modelData, data, "linear_regression");
        double[] weights = weightsArray(modelData, data.featureCount());
        double bias = modelData.getBias() == null ? 0.0 : modelData.getBias();
        double loss = 0.0;
        double sse = 0.0;
        double sst = 0.0;

        for (DataRow row : data.rows) {
            double prediction = dotNormalized(data, weights, row) + bias;
            double error = prediction - data.normalizeTarget(row.target);
            loss += error * error;
            double rawPrediction = data.denormalizeTarget(prediction);
            double rawError = rawPrediction - row.target;
            sse += rawError * rawError;
            double targetCenter = row.target - data.targetMean;
            sst += targetCenter * targetCenter;
        }

        int n = Math.max(1, data.rows.size());
        double mse = loss / n;
        double r2 = sst < EPSILON ? 1.0 : 1.0 - sse / sst;
        modelData.setRegressionLine(defaultRegressionLine(data, dataset, modelData.getWeights(), bias));
        return new StepResult(modelData, round(mse), round(clamp(r2, 0.0, 0.99)), "R2");
    }

    private StepResult trainLogisticRegression(Dataset dataset, String parameters, TrainingSimulationResponse.ModelData modelData) {
        DataMatrix data = loadDataMatrix(dataset, TargetMode.SUPERVISED);
        applyMatrixMetadata(modelData, data, "logistic_regression");
        int featureCount = data.featureCount();
        double[] weights = weightsArray(modelData, featureCount);
        double bias = modelData.getBias() == null ? 0.0 : modelData.getBias();
        double learningRate = clamp(getNumericParameter(parameters, "learningRate", 0.08), 0.0001, 0.8);
        double momentum = 0.35;

        double[] gradient = new double[featureCount];
        double gradientBias = 0.0;
        for (DataRow row : data.rows) {
            double probability = sigmoid(dotNormalized(data, weights, row) + bias);
            double error = probability - row.classValue;
            for (int index = 0; index < featureCount; index++) {
                gradient[index] += error * data.normalizeFeature(index, row.features[index]);
            }
            gradientBias += error;
        }
        int n = Math.max(1, data.rows.size());
        for (int index = 0; index < featureCount; index++) {
            double targetWeight = weights[index] - learningRate * gradient[index] / n;
            weights[index] = weights[index] + (targetWeight - weights[index]) * momentum;
        }
        double targetBias = bias - learningRate * gradientBias / n;
        bias = bias + (targetBias - bias) * momentum;

        modelData.setWeights(roundWeights(weights));
        modelData.setBias(round(bias));
        modelData.setDecisionBoundary(defaultBoundary(data, dataset, modelData.getWeights(), modelData.getBias()));
        return evaluateLogisticRegression(dataset, modelData);
    }

    private StepResult evaluateLogisticRegression(Dataset dataset, TrainingSimulationResponse.ModelData modelData) {
        DataMatrix data = loadDataMatrix(dataset, TargetMode.SUPERVISED);
        applyMatrixMetadata(modelData, data, "logistic_regression");
        double[] weights = weightsArray(modelData, data.featureCount());
        double bias = modelData.getBias() == null ? 0.0 : modelData.getBias();
        double loss = 0.0;
        int correct = 0;

        for (DataRow row : data.rows) {
            double target = row.classValue;
            double probability = clamp(sigmoid(dotNormalized(data, weights, row) + bias), EPSILON, 1.0 - EPSILON);
            loss += -(target * Math.log(probability) + (1.0 - target) * Math.log(1.0 - probability));
            int predicted = probability >= 0.5 ? 1 : 0;
            if (predicted == (int) target) {
                correct += 1;
            }
        }

        modelData.setDecisionBoundary(defaultBoundary(data, dataset, modelData.getWeights(), bias));
        double accuracy = data.rows.isEmpty() ? 0.0 : (double) correct / data.rows.size();
        return new StepResult(modelData, round(loss / Math.max(1, data.rows.size())), round(accuracy), "Accuracy");
    }

    private StepResult trainKMeans(Dataset dataset, String parameters, TrainingSimulationResponse.ModelData modelData) {
        DataMatrix data = loadDataMatrix(dataset, TargetMode.NONE);
        applyMatrixMetadata(modelData, data, "kmeans");
        int k = getK(parameters);
        List<TrainingSimulationResponse.Centroid> centroids = modelData.getCentroids();
        if (!isUsableCentroids(centroids, k, data.featureCount())) {
            centroids = initializeCentroids(data, k);
        }

        List<Integer> assignments = assignClusters(data, centroids);
        List<TrainingSimulationResponse.Centroid> updated = recomputeCentroids(data, assignments, centroids);
        modelData.setCentroids(updated);
        modelData.setAssignments(assignClusters(data, updated));
        return evaluateKMeans(dataset, modelData);
    }

    private StepResult evaluateKMeans(Dataset dataset, TrainingSimulationResponse.ModelData modelData) {
        DataMatrix data = loadDataMatrix(dataset, TargetMode.NONE);
        applyMatrixMetadata(modelData, data, "kmeans");
        List<TrainingSimulationResponse.Centroid> centroids = modelData.getCentroids();
        if (!isUsableCentroids(centroids, centroids == null ? 0 : centroids.size(), data.featureCount())) {
            centroids = initializeCentroids(data, 3);
            modelData.setCentroids(centroids);
        }
        List<Integer> assignments = assignClusters(data, centroids);
        modelData.setAssignments(assignments);

        double inertia = 0.0;
        for (int index = 0; index < data.rows.size(); index++) {
            TrainingSimulationResponse.Centroid centroid = centroids.get(assignments.get(index));
            inertia += normalizedSquaredDistance(data, data.rows.get(index), centroid);
        }

        double normalizedInertia = normalizeInertia(inertia, data.rows.size(), data.featureCount());
        double silhouette = approximateSilhouette(data, assignments, centroids);
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

    // ==================== 全维度数据层 ====================

    /**
     * 数据集在训练时使用的两个"默认投影"维度。
     * 规则与 {@link DatasetService#getAllDatasets} 的投影保持一致，
     * 决策树 / SVM 这两条尚未全维化的路径也复用这里，避免两处规则漂移。
     */
    private List<String> trainingAxes(Dataset dataset) {
        List<FeatureInfo> features = parseFeatures(dataset.getFeatures());
        String labelName = features.stream()
                .filter(feature -> "label".equals(feature.type))
                .map(feature -> feature.name)
                .findFirst()
                .orElse(null);
        String xName = preferredFeature(features, List.of("rm", "x", "petal_length", "sepal_length"));
        String yName = preferredFeature(features, List.of("y", "petal_width", "sepal_width"));

        if (xName == null) {
            xName = firstNonLabelFeature(features, null);
        }
        if (yName == null && labelName != null) {
            yName = labelName;
        }
        if (yName == null) {
            yName = firstNonLabelFeature(features, xName);
        }
        return Arrays.asList(xName, yName);
    }

    private String firstNonLabelFeature(List<FeatureInfo> features, String excludedName) {
        return features.stream()
                .filter(feature -> !"label".equals(feature.type))
                .map(feature -> feature.name)
                .filter(name -> excludedName == null || !excludedName.equals(name))
                .findFirst()
                .orElse(null);
    }

    private enum TargetMode {
        /** 监督学习：用 label 列当目标；没有 label 列时把最后一维当目标（回归退化场景） */
        SUPERVISED,
        /** 无监督：所有数值维都是特征，不需要目标 */
        NONE
    }

    private record DataRow(double[] features, double target, double classValue, int classIndex) {}

    /**
     * 全维度训练矩阵：所有数值维度 + 逐维 z-score 参数。
     * <p>
     * 用 z-score 而不是 min-max，除了量级差异（b=396.9 / crim=0.006）下梯度更稳，
     * 还让"其余维度固定在均值"在归一化空间里恰好等于 0，投影公式退化成只用所选两维系数的直线。
     */
    private static final class DataMatrix {
        private final List<String> featureNames;
        private final List<DataRow> rows;
        private final double[] means;
        private final double[] scales;
        private final double[] mins;
        private final double[] maxs;
        private final double targetMean;
        private final double targetScale;
        private final String targetName;

        private DataMatrix(
                List<String> featureNames,
                List<DataRow> rows,
                double[] means,
                double[] scales,
                double[] mins,
                double[] maxs,
                double targetMean,
                double targetScale,
                String targetName) {
            this.featureNames = featureNames;
            this.rows = rows;
            this.means = means;
            this.scales = scales;
            this.mins = mins;
            this.maxs = maxs;
            this.targetMean = targetMean;
            this.targetScale = targetScale;
            this.targetName = targetName;
        }

        private int featureCount() {
            return featureNames.size();
        }

        private double range(int index) {
            return Math.max(EPSILON, maxs[index] - mins[index]);
        }

        private int featureIndex(String name) {
            return name == null ? -1 : featureNames.indexOf(name);
        }

        private double normalizeFeature(int index, double raw) {
            return (raw - means[index]) / Math.max(EPSILON, scales[index]);
        }

        private double denormalizeFeature(int index, double normalized) {
            return normalized * Math.max(EPSILON, scales[index]) + means[index];
        }

        private double normalizeTarget(double raw) {
            return (raw - targetMean) / Math.max(EPSILON, targetScale);
        }

        private double denormalizeTarget(double normalized) {
            return normalized * Math.max(EPSILON, targetScale) + targetMean;
        }
    }

    private DataMatrix loadDataMatrix(Dataset dataset, TargetMode mode) {
        List<FeatureInfo> declared = parseFeatures(dataset.getFeatures());
        List<LinkedHashMap<String, Object>> rawRows = parseRows(dataset.getDataContent());
        String labelName = declared.stream()
                .filter(feature -> "label".equals(feature.type))
                .map(feature -> feature.name)
                .findFirst()
                .orElse(null);

        List<String> featureNames = new ArrayList<>();
        for (FeatureInfo feature : declared) {
            if ("label".equals(feature.type)) {
                continue;
            }
            if (isNumericColumn(rawRows, feature.name)) {
                featureNames.add(feature.name);
            }
        }

        String targetName = null;
        if (mode == TargetMode.SUPERVISED) {
            if (labelName != null) {
                targetName = labelName;
            } else if (!featureNames.isEmpty()) {
                // 没有标签列时退化为"最后一维当目标"，并从特征里剔除，避免目标泄漏
                targetName = featureNames.remove(featureNames.size() - 1);
            }
        }

        int featureCount = featureNames.size();
        int rowCount = rawRows.size();
        double[][] values = new double[rowCount][featureCount];
        double[] targetValues = new double[rowCount];
        double[] classValues = new double[rowCount];
        int[] classIndexes = new int[rowCount];
        double[] sums = new double[featureCount];
        double[] sumSquares = new double[featureCount];
        int[] counts = new int[featureCount];
        Map<String, Integer> labelMap = new HashMap<>();

        for (int i = 0; i < rowCount; i++) {
            LinkedHashMap<String, Object> row = rawRows.get(i);
            for (int j = 0; j < featureCount; j++) {
                Double value = asDouble(row.get(featureNames.get(j)));
                if (value == null) {
                    values[i][j] = Double.NaN; // 缺失值稍后用列均值补齐，保证行下标与前端 dataPoints 对齐
                } else {
                    values[i][j] = value;
                    sums[j] += value;
                    sumSquares[j] += value * value;
                    counts[j] += 1;
                }
            }
            Object rawLabel = targetName == null ? null : row.get(targetName);
            int classIndex = rawLabel == null ? 0 : toClassIndex(rawLabel, labelMap);
            classIndexes[i] = classIndex;
            classValues[i] = classIndex == 0 ? 0.0 : 1.0;
            Double numericTarget = asDouble(rawLabel);
            targetValues[i] = numericTarget != null ? numericTarget : classValues[i];
        }

        double[] means = new double[featureCount];
        double[] scales = new double[featureCount];
        double[] mins = new double[featureCount];
        double[] maxs = new double[featureCount];
        for (int j = 0; j < featureCount; j++) {
            means[j] = counts[j] == 0 ? 0.0 : sums[j] / counts[j];
            double variance = counts[j] == 0
                    ? 0.0
                    : Math.max(0.0, sumSquares[j] / counts[j] - means[j] * means[j]);
            scales[j] = Math.max(EPSILON, Math.sqrt(variance));
            mins[j] = Double.MAX_VALUE;
            maxs[j] = -Double.MAX_VALUE;
        }
        for (int i = 0; i < rowCount; i++) {
            for (int j = 0; j < featureCount; j++) {
                if (Double.isNaN(values[i][j])) {
                    values[i][j] = means[j];
                }
                mins[j] = Math.min(mins[j], values[i][j]);
                maxs[j] = Math.max(maxs[j], values[i][j]);
            }
        }
        for (int j = 0; j < featureCount; j++) {
            if (mins[j] > maxs[j]) { // 没有任何数据时退化成均值点
                mins[j] = means[j];
                maxs[j] = means[j];
            }
        }

        double targetMean = 0.0;
        double targetVariance = 0.0;
        if (rowCount > 0) {
            double targetSum = 0.0;
            for (double value : targetValues) {
                targetSum += value;
            }
            targetMean = targetSum / rowCount;
            for (double value : targetValues) {
                targetVariance += (value - targetMean) * (value - targetMean);
            }
            targetVariance /= rowCount;
        }
        double targetScale = Math.max(EPSILON, Math.sqrt(targetVariance));

        List<DataRow> rows = new ArrayList<>(rowCount);
        for (int i = 0; i < rowCount; i++) {
            rows.add(new DataRow(values[i], targetValues[i], classValues[i], classIndexes[i]));
        }
        return new DataMatrix(
                featureNames, rows, means, scales, mins, maxs, targetMean, targetScale, targetName);
    }

    /** 声明为数值、且数据里至少一半能解析成数字的列才算特征；纯文本列（如某些标签）不参与训练 */
    private boolean isNumericColumn(List<LinkedHashMap<String, Object>> rows, String name) {
        int total = 0;
        int numeric = 0;
        for (LinkedHashMap<String, Object> row : rows) {
            if (!row.containsKey(name)) {
                continue;
            }
            total += 1;
            if (asDouble(row.get(name)) != null) {
                numeric += 1;
            }
        }
        return total > 0 && numeric * 2 >= total;
    }

    private double[] weightsArray(TrainingSimulationResponse.ModelData modelData, int size) {
        return weightsArray(modelData.getWeights(), size);
    }

    private double[] weightsArray(List<Double> weights, int size) {
        double[] result = new double[Math.max(0, size)];
        if (weights == null) {
            return result;
        }
        for (int index = 0; index < result.length && index < weights.size(); index++) {
            Double value = weights.get(index);
            result[index] = value == null ? 0.0 : value;
        }
        return result;
    }

    private List<Double> roundWeights(double[] weights) {
        List<Double> rounded = new ArrayList<>(weights.length);
        for (double weight : weights) {
            rounded.add(round(weight));
        }
        return rounded;
    }

    private double dotNormalized(DataMatrix data, double[] weights, DataRow row) {
        double sum = 0.0;
        int size = Math.min(weights.length, data.featureCount());
        for (int index = 0; index < size; index++) {
            sum += weights[index] * data.normalizeFeature(index, row.features[index]);
        }
        return sum;
    }

    /** 默认投影下的纵轴维度：优先取数据集默认投影轴，其次取第一个未被占用的维度 */
    private int defaultProjectionIndex(DataMatrix data, Dataset dataset, int axis, int excludedIndex) {
        List<String> axes = trainingAxes(dataset);
        int preferred = axis < axes.size() ? data.featureIndex(axes.get(axis)) : -1;
        if (preferred >= 0 && preferred != excludedIndex) {
            return preferred;
        }
        for (int candidate = 0; candidate < data.featureCount(); candidate++) {
            if (candidate != excludedIndex) {
                return candidate;
            }
        }
        return -1;
    }

    private List<List<Double>> defaultRegressionLine(
            DataMatrix data,
            Dataset dataset,
            List<Double> weights,
            double bias) {
        return buildFullRegressionLine(data, weights, bias, defaultProjectionIndex(data, dataset, 0, -1));
    }

    private List<List<Double>> defaultBoundary(
            DataMatrix data,
            Dataset dataset,
            List<Double> weights,
            double bias) {
        int xIndex = defaultProjectionIndex(data, dataset, 0, -1);
        int yIndex = defaultProjectionIndex(data, dataset, 1, xIndex);
        return buildFullLinearBoundary(data, xIndex, yIndex, weights, bias);
    }

    private DataSet2d loadDataSet(Dataset dataset, Algorithm algorithm) {
        List<DataPoint2d> points = new ArrayList<>();
        List<FeatureInfo> features = parseFeatures(dataset.getFeatures());
        String labelName = features.stream()
                .filter(feature -> "label".equals(feature.type))
                .map(feature -> feature.name)
                .findFirst()
                .orElse(null);
        List<String> axes = trainingAxes(dataset);
        String preferredX = axes.get(0);
        String preferredY = axes.get(1);
        Map<String, Integer> labelMap = new HashMap<>();

        for (LinkedHashMap<String, Object> row : parseRows(dataset.getDataContent())) {
            Double target = labelName == null ? null : asDouble(row.get(labelName));
            Object rawLabel = labelName == null ? row.get("label") : row.get(labelName);
            int classIndex = toClassIndex(rawLabel, labelMap);
            double classValue = classIndex == 0 ? 0.0 : 1.0;
            Double x = preferredX == null ? null : asDouble(row.get(preferredX));
            Double y = preferredY == null ? null : asDouble(row.get(preferredY));

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

    private List<TrainingSimulationResponse.Centroid> initializeCentroids(DataMatrix data, int k) {
        List<TrainingSimulationResponse.Centroid> centroids = new ArrayList<>();
        int featureCount = data.featureCount();
        for (int index = 0; index < k; index++) {
            List<Double> values = new ArrayList<>(featureCount);
            for (int j = 0; j < featureCount; j++) {
                double position = (index + 1.0) / (k + 1.0);
                // 奇数维加一点错位，避免所有质心落在同一条对角线上
                double jitter = j % 2 == 0 ? 0.0 : (((index % 2) + 1) / 3.0 - 0.5) * 0.6;
                values.add(round(data.mins[j] + data.range(j) * clamp(position + jitter, 0.05, 0.95)));
            }
            centroids.add(buildCentroid(values, index));
        }
        return centroids;
    }

    private TrainingSimulationResponse.Centroid buildCentroid(List<Double> values, int clusterId) {
        TrainingSimulationResponse.Centroid centroid = new TrainingSimulationResponse.Centroid();
        centroid.setClusterId(clusterId);
        centroid.setValues(values);
        centroid.setX(values.isEmpty() ? 0.0 : values.get(0));
        centroid.setY(values.size() < 2 ? 0.0 : values.get(1));
        return centroid;
    }

    /** 质心必须是本次数据集维度下算出来的：老会话里存的两维质心（没有 values）要重新初始化 */
    private boolean isUsableCentroids(
            List<TrainingSimulationResponse.Centroid> centroids,
            int expectedCount,
            int featureCount) {
        if (centroids == null || centroids.isEmpty() || centroids.size() != expectedCount) {
            return false;
        }
        for (TrainingSimulationResponse.Centroid centroid : centroids) {
            if (centroid.getValues() == null || centroid.getValues().size() != featureCount) {
                return false;
            }
        }
        return true;
    }

    private List<Integer> assignClusters(DataMatrix data, List<TrainingSimulationResponse.Centroid> centroids) {
        List<Integer> assignments = new ArrayList<>(data.rows.size());
        for (DataRow row : data.rows) {
            int bestIndex = 0;
            double bestDistance = Double.MAX_VALUE;
            for (int index = 0; index < centroids.size(); index++) {
                double distance = normalizedSquaredDistance(data, row, centroids.get(index));
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestIndex = index;
                }
            }
            assignments.add(bestIndex);
        }
        return assignments;
    }

    /** 归一化空间里的平方距离：b=396.9 与 crim=0.006 这类量级差必须先按维度缩放再比较 */
    private double normalizedSquaredDistance(
            DataMatrix data,
            DataRow row,
            TrainingSimulationResponse.Centroid centroid) {
        List<Double> values = centroid.getValues();
        int featureCount = Math.min(values == null ? 0 : values.size(), data.featureCount());
        double total = 0.0;
        for (int j = 0; j < featureCount; j++) {
            double diff = data.normalizeFeature(j, row.features[j]) - data.normalizeFeature(j, values.get(j));
            total += diff * diff;
        }
        return total;
    }

    private List<TrainingSimulationResponse.Centroid> recomputeCentroids(
            DataMatrix data,
            List<Integer> assignments,
            List<TrainingSimulationResponse.Centroid> previous) {
        double momentum = 0.35;
        int featureCount = data.featureCount();
        List<TrainingSimulationResponse.Centroid> updated = new ArrayList<>();
        for (int clusterId = 0; clusterId < previous.size(); clusterId++) {
            double[] sums = new double[featureCount];
            int count = 0;
            for (int index = 0; index < data.rows.size(); index++) {
                if (assignments.get(index) != clusterId) {
                    continue;
                }
                DataRow row = data.rows.get(index);
                for (int j = 0; j < featureCount; j++) {
                    sums[j] += row.features[j];
                }
                count += 1;
            }
            List<Double> previousValues = previous.get(clusterId).getValues();
            List<Double> values = new ArrayList<>(featureCount);
            for (int j = 0; j < featureCount; j++) {
                double prev = previousValues != null && previousValues.size() > j
                        ? previousValues.get(j)
                        : data.means[j];
                values.add(round(count == 0 ? prev : prev + (sums[j] / count - prev) * momentum));
            }
            updated.add(buildCentroid(values, clusterId));
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

    // ==================== 全维度模型在任意两维平面上的投影 ====================

    /**
     * 全维度回归线在 (xIndex, 目标) 平面上的投影。
     * <p>
     * 隐藏维度固定在均值，z-score 后它们的贡献恰好为 0，
     * 于是曲线就是"用该维度的斜率画线"：ŷ = 反归一化(w[x] * x̃ + b)。
     */
    private List<List<Double>> buildFullRegressionLine(
            DataMatrix data,
            List<Double> weights,
            double bias,
            int xIndex) {
        if (data.rows.isEmpty() || xIndex < 0 || xIndex >= data.featureCount()) {
            return null;
        }
        double[] coefficients = weightsArray(weights, data.featureCount());
        double minX = data.mins[xIndex];
        double maxX = data.maxs[xIndex];
        double y1 = data.denormalizeTarget(coefficients[xIndex] * data.normalizeFeature(xIndex, minX) + bias);
        double y2 = data.denormalizeTarget(coefficients[xIndex] * data.normalizeFeature(xIndex, maxX) + bias);
        return Arrays.asList(
                Arrays.asList(round(minX), round(y1)),
                Arrays.asList(round(maxX), round(y2)));
    }

    /**
     * 全维度线性决策边界在 (xIndex, yIndex) 平面上的投影：
     * w[x]·x̃ + w[y]·ỹ + b = 0（隐藏维度固定在均值，贡献为 0）。
     */
    private List<List<Double>> buildFullLinearBoundary(
            DataMatrix data,
            int xIndex,
            int yIndex,
            List<Double> weights,
            double bias) {
        if (data.rows.isEmpty() || xIndex < 0 || yIndex < 0 || xIndex == yIndex) {
            return null;
        }
        if (xIndex >= data.featureCount() || yIndex >= data.featureCount()) {
            return null;
        }
        double[] coefficients = weightsArray(weights, data.featureCount());
        double wx = coefficients[xIndex];
        double wy = coefficients[yIndex];
        double minX = data.mins[xIndex];
        double maxX = data.maxs[xIndex];
        double minY = data.mins[yIndex];
        double maxY = data.maxs[yIndex];

        if (Math.abs(wy) < EPSILON) {
            if (Math.abs(wx) < EPSILON) {
                return null; // 两个维度的系数都是 0，无法确定边界
            }
            double x = data.denormalizeFeature(xIndex, -bias / wx);
            return Arrays.asList(
                    Arrays.asList(round(x), round(minY)),
                    Arrays.asList(round(x), round(maxY)));
        }
        double y1 = data.denormalizeFeature(
                yIndex, (-bias - wx * data.normalizeFeature(xIndex, minX)) / wy);
        double y2 = data.denormalizeFeature(
                yIndex, (-bias - wx * data.normalizeFeature(xIndex, maxX)) / wy);
        return Arrays.asList(
                Arrays.asList(round(minX), round(y1)),
                Arrays.asList(round(maxX), round(y2)));
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
            DataMatrix data,
            List<Integer> assignments,
            List<TrainingSimulationResponse.Centroid> centroids) {
        if (centroids.size() <= 1 || data.rows.isEmpty()) {
            return 0.0;
        }
        double total = 0.0;
        for (int index = 0; index < data.rows.size(); index++) {
            DataRow row = data.rows.get(index);
            int ownCluster = assignments.get(index);
            double a = Math.sqrt(normalizedSquaredDistance(data, row, centroids.get(ownCluster)));
            double b = Double.MAX_VALUE;
            for (int clusterId = 0; clusterId < centroids.size(); clusterId++) {
                if (clusterId == ownCluster) {
                    continue;
                }
                b = Math.min(b, Math.sqrt(normalizedSquaredDistance(data, row, centroids.get(clusterId))));
            }
            total += (b - a) / Math.max(a, b);
        }
        return clamp((total / data.rows.size() + 1.0) / 2.0, 0.0, 0.99);
    }

    /** 归一化空间中每维标准差为 1，簇内平方距离量级约为维度数，故用 n * m * 4 归一到 0~1 */
    private double normalizeInertia(double inertia, int sampleCount, int featureCount) {
        double scale = Math.max(EPSILON, sampleCount * Math.max(1, featureCount) * 4.0);
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

    private String buildFeaturesJson(List<String> selectedFeatures, Dataset dataset, String labelName) {
        List<FeatureInfo> original = parseFeatures(dataset.getFeatures());
        List<Map<String, Object>> out = new ArrayList<>();
        for (FeatureInfo f : original) {
            boolean keep = selectedFeatures.contains(f.name())
                    || "label".equals(f.type())
                    || (labelName != null && labelName.equals(f.name()));
            if (keep) {
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
