package com.mlplatform.dto;

import java.util.List;

/**
 * 模型在用户所选两个维度上的投影视图。
 * <p>
 * 全维度算法（线性回归 / 逻辑回归 / K-Means）会在该平面上重新求一次模型切片：
 * 隐藏维度固定在各自的均值上，因此线性模型的投影就是一条直线（斜率取自对应维度的系数）。
 * 仍按两维训练的算法（决策树 / SVM）只在请求的两维等于训练维度时回放过往的几何结果。
 */
public class ProjectionDTO {

    /** 叠加层种类，前端据此决定线型与图例文案 */
    public static final String KIND_REGRESSION_LINE = "regression_line";
    public static final String KIND_DECISION_BOUNDARY = "decision_boundary";
    public static final String KIND_CENTROIDS = "centroids";
    public static final String KIND_NONE = "none";

    private Long sessionId;
    private String xName;
    private String yName;
    private String kind;
    /** 回归线 / 决策边界的折线点（原始单位坐标） */
    private List<List<Double>> line;
    /** 聚类中心在所选两维上的投影 */
    private List<TrainingSimulationResponse.Centroid> centroids;
    /** 每个样本的簇编号，按数据行下标对齐（全维度聚类结果，任意坐标轴都成立） */
    private List<Integer> assignments;
    /** 画布上要显示的说明文案；无说明时为 null */
    private String note;

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public String getXName() { return xName; }
    public void setXName(String xName) { this.xName = xName; }
    public String getYName() { return yName; }
    public void setYName(String yName) { this.yName = yName; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public List<List<Double>> getLine() { return line; }
    public void setLine(List<List<Double>> line) { this.line = line; }
    public List<TrainingSimulationResponse.Centroid> getCentroids() { return centroids; }
    public void setCentroids(List<TrainingSimulationResponse.Centroid> centroids) { this.centroids = centroids; }
    public List<Integer> getAssignments() { return assignments; }
    public void setAssignments(List<Integer> assignments) { this.assignments = assignments; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
