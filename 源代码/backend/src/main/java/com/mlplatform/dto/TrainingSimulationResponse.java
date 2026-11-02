package com.mlplatform.dto;

import java.util.List;

public class TrainingSimulationResponse {
    private Long sessionId;
    private Long algorithmId;
    private Long datasetId;
    private String status;
    private Integer currentStep;
    private Integer totalSteps;
    private Double loss;
    private Double accuracy;
    private String scoreLabel;
    private Metrics metrics;
    private ModelData modelData;

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public Long getAlgorithmId() { return algorithmId; }
    public void setAlgorithmId(Long algorithmId) { this.algorithmId = algorithmId; }
    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long datasetId) { this.datasetId = datasetId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getCurrentStep() { return currentStep; }
    public void setCurrentStep(Integer currentStep) { this.currentStep = currentStep; }
    public Integer getTotalSteps() { return totalSteps; }
    public void setTotalSteps(Integer totalSteps) { this.totalSteps = totalSteps; }
    public Double getLoss() { return loss; }
    public void setLoss(Double loss) { this.loss = loss; }
    public Double getAccuracy() { return accuracy; }
    public void setAccuracy(Double accuracy) { this.accuracy = accuracy; }
    public String getScoreLabel() { return scoreLabel; }
    public void setScoreLabel(String scoreLabel) { this.scoreLabel = scoreLabel; }
    public Metrics getMetrics() { return metrics; }
    public void setMetrics(Metrics metrics) { this.metrics = metrics; }
    public ModelData getModelData() { return modelData; }
    public void setModelData(ModelData modelData) { this.modelData = modelData; }

    public static class Metrics {
        private List<MetricPoint> points;
        private String lossLabel;
        private String scoreLabel;

        public List<MetricPoint> getPoints() { return points; }
        public void setPoints(List<MetricPoint> points) { this.points = points; }
        public String getLossLabel() { return lossLabel; }
        public void setLossLabel(String lossLabel) { this.lossLabel = lossLabel; }
        public String getScoreLabel() { return scoreLabel; }
        public void setScoreLabel(String scoreLabel) { this.scoreLabel = scoreLabel; }
    }

    public static class MetricPoint {
        private Integer step;
        private Double loss;
        private Double score;

        public Integer getStep() { return step; }
        public void setStep(Integer step) { this.step = step; }
        public Double getLoss() { return loss; }
        public void setLoss(Double loss) { this.loss = loss; }
        public Double getScore() { return score; }
        public void setScore(Double score) { this.score = score; }
    }

    public static class ModelData {
        private List<List<Double>> decisionBoundary;
        private List<List<Double>> regressionLine;
        private List<Centroid> centroids;
        private List<Double> weights;
        private Double bias;
        /**
         * 全维度模型的元信息：算法种类、训练所用的全部维度名、监督学习的目标维度名。
         * 决策树 / 随机森林 / SVM 仍在两维上训练，这些字段可能为 null。
         */
        private String modelKind;
        private List<String> featureNames;
        private String targetName;
        /** 类别编号 → 原始标签名（如 0/1/2 → setosa/versicolor/virginica），用于按预测类别着色时的图例 */
        private List<String> classNames;
        /** 贪心决策树的完整节点表（按 BFS 顺序编号）；revealedSplits 之前的分裂才是"当前已展开"的部分 */
        private List<TreeNode> tree;
        private Integer revealedSplits;
        private List<Integer> assignments;
        private String splitFeature;
        private Double splitThreshold;
        private Double splitImpurity;
        private List<List<Double>> pcaComponents;
        private List<List<Double>> pcaTransformed;
        private List<FeatureImportance> featureImportances;

        public List<List<Double>> getDecisionBoundary() { return decisionBoundary; }
        public void setDecisionBoundary(List<List<Double>> decisionBoundary) { this.decisionBoundary = decisionBoundary; }
        public List<List<Double>> getRegressionLine() { return regressionLine; }
        public void setRegressionLine(List<List<Double>> regressionLine) { this.regressionLine = regressionLine; }
        public List<Centroid> getCentroids() { return centroids; }
        public void setCentroids(List<Centroid> centroids) { this.centroids = centroids; }
        public List<Double> getWeights() { return weights; }
        public void setWeights(List<Double> weights) { this.weights = weights; }
        public Double getBias() { return bias; }
        public void setBias(Double bias) { this.bias = bias; }
        public String getModelKind() { return modelKind; }
        public void setModelKind(String modelKind) { this.modelKind = modelKind; }
        public List<String> getFeatureNames() { return featureNames; }
        public void setFeatureNames(List<String> featureNames) { this.featureNames = featureNames; }
        public String getTargetName() { return targetName; }
        public void setTargetName(String targetName) { this.targetName = targetName; }
        public List<String> getClassNames() { return classNames; }
        public void setClassNames(List<String> classNames) { this.classNames = classNames; }
        public List<TreeNode> getTree() { return tree; }
        public void setTree(List<TreeNode> tree) { this.tree = tree; }
        public Integer getRevealedSplits() { return revealedSplits; }
        public void setRevealedSplits(Integer revealedSplits) { this.revealedSplits = revealedSplits; }
        public List<Integer> getAssignments() { return assignments; }
        public void setAssignments(List<Integer> assignments) { this.assignments = assignments; }
        public String getSplitFeature() { return splitFeature; }
        public void setSplitFeature(String splitFeature) { this.splitFeature = splitFeature; }
        public Double getSplitThreshold() { return splitThreshold; }
        public void setSplitThreshold(Double splitThreshold) { this.splitThreshold = splitThreshold; }
        public Double getSplitImpurity() { return splitImpurity; }
        public void setSplitImpurity(Double splitImpurity) { this.splitImpurity = splitImpurity; }
        public List<List<Double>> getPcaComponents() { return pcaComponents; }
        public void setPcaComponents(List<List<Double>> pcaComponents) { this.pcaComponents = pcaComponents; }
        public List<List<Double>> getPcaTransformed() { return pcaTransformed; }
        public void setPcaTransformed(List<List<Double>> pcaTransformed) { this.pcaTransformed = pcaTransformed; }
        public List<FeatureImportance> getFeatureImportances() { return featureImportances; }
        public void setFeatureImportances(List<FeatureImportance> featureImportances) { this.featureImportances = featureImportances; }
    }

    public static class Centroid {
        private Double x;
        private Double y;
        private Integer clusterId;
        /** 全维度质心坐标（按 ModelData.featureNames 顺序）；旧的 x / y 是它在默认投影两维上的取值 */
        private List<Double> values;

        public Double getX() { return x; }
        public void setX(Double x) { this.x = x; }
        public Double getY() { return y; }
        public void setY(Double y) { this.y = y; }
        public Integer getClusterId() { return clusterId; }
        public void setClusterId(Integer clusterId) { this.clusterId = clusterId; }
        public List<Double> getValues() { return values; }
        public void setValues(List<Double> values) { this.values = values; }
    }

    public static class FeatureImportance {
        private String feature;
        private Double importance;

        public String getFeature() { return feature; }
        public void setFeature(String feature) { this.feature = feature; }
        public Double getImportance() { return importance; }
        public void setImportance(Double importance) { this.importance = importance; }
    }

    /**
     * 贪心决策树的一个节点。
     * <p>
     * 整棵树在一次构建里算完，但只有 splitOrder 小于 revealedSplits 的节点才真正"展开"；
     * 其余节点按叶子处理（用 classIndex 预测），因此每次训练步只要把 revealedSplits 加一，
     * 模型就恰好停在"最近一次分裂"的状态上。
     */
    public static class TreeNode {
        private Integer id;
        private Integer depth;
        /** null 表示这是叶子 */
        private String feature;
        /** 该特征在 ModelData.featureNames 里的下标，用于按行取值向下路由 */
        private Integer featureIndex;
        private Double threshold;
        /** 该节点自身的不纯度（展开成叶子时也用它作为这部分数据的损失） */
        private Double impurity;
        /** 该节点的多数类；节点尚未展开时就以它作为预测 */
        private Integer classIndex;
        private Integer leftId;
        private Integer rightId;
        /** 第几个被展开（BFS 顺序）；-1 表示这棵树里它永远不会分裂 */
        private Integer splitOrder;

        public Integer getId() { return id; }
        public void setId(Integer id) { this.id = id; }
        public Integer getDepth() { return depth; }
        public void setDepth(Integer depth) { this.depth = depth; }
        public String getFeature() { return feature; }
        public void setFeature(String feature) { this.feature = feature; }
        public Integer getFeatureIndex() { return featureIndex; }
        public void setFeatureIndex(Integer featureIndex) { this.featureIndex = featureIndex; }
        public Double getThreshold() { return threshold; }
        public void setThreshold(Double threshold) { this.threshold = threshold; }
        public Double getImpurity() { return impurity; }
        public void setImpurity(Double impurity) { this.impurity = impurity; }
        public Integer getClassIndex() { return classIndex; }
        public void setClassIndex(Integer classIndex) { this.classIndex = classIndex; }
        public Integer getLeftId() { return leftId; }
        public void setLeftId(Integer leftId) { this.leftId = leftId; }
        public Integer getRightId() { return rightId; }
        public void setRightId(Integer rightId) { this.rightId = rightId; }
        public Integer getSplitOrder() { return splitOrder; }
        public void setSplitOrder(Integer splitOrder) { this.splitOrder = splitOrder; }
    }
}
