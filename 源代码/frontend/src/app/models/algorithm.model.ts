export interface Dataset {
  id: number;
  name: string;
  description: string;
  category: string;
  featureCount: number;
  sampleCount: number;
  features: FeatureInfo[];
  dataPoints: DataPoint[];
  /** 数据集声明的全部维度（仅详情接口返回），用于坐标轴选择 */
  dimensions?: FeatureInfo[];
}

export interface FeatureInfo {
  name: string;
  displayName: string;
  type: string;
}

export interface DataPoint {
  x: number;
  y: number;
  label?: string;
  clusterId?: number;
  /** 该行各维度的原始值（仅详情接口返回），键为特征名 */
  values?: { [name: string]: number };
}

export interface Algorithm {
  id: number;
  name: string;
  displayName: string;
  description: string;
  category: string;
  type: string;
  parameters: ParameterDef[];
}

export interface ParameterDef {
  name: string;
  displayName: string;
  type: string;
  default: any;
  min?: number;
  max?: number;
  step?: number;
  options?: string[];
}

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}

export interface TrainingMetricPoint {
  step: number;
  loss: number;
  score: number;
}

export interface TrainingMetrics {
  points: TrainingMetricPoint[];
  lossLabel: string;
  scoreLabel: string;
}

export interface TrainingCentroid {
  x: number;
  y: number;
  clusterId: number;
  /** 全维度质心坐标（按 modelData.featureNames 顺序） */
  values?: number[];
}

export interface TrainingModelData {
  decisionBoundary?: number[][];
  regressionLine?: number[][];
  centroids?: TrainingCentroid[];
  assignments?: number[];
  pcaComponents?: number[][];
  pcaTransformed?: number[][];
  featureImportances?: FeatureImportance[];
  /** 全维度模型自述：算法种类 / 训练所用维度 / 监督目标维度 */
  modelKind?: string;
  featureNames?: string[];
  targetName?: string;
}

/** 模型在用户所选两个维度上的投影，由后端 /visualization/projection 计算 */
export interface TrainingProjection {
  sessionId?: number;
  xName: string;
  yName: string;
  /** regression_line / decision_boundary / centroids / none */
  kind: string;
  /** 回归线或决策边界的折线点 */
  line?: number[][];
  centroids?: TrainingCentroid[];
  assignments?: number[];
  /** 画布上要显示的说明文案 */
  note?: string;
}

export interface FeatureImportance {
  feature: string;
  importance: number;
}

export interface TrainingSimulationResponse {
  sessionId: number;
  algorithmId: number;
  datasetId: number;
  status: TrainingState['status'];
  currentStep: number;
  totalSteps: number;
  loss: number;
  accuracy: number;
  scoreLabel: string;
  metrics: TrainingMetrics;
  modelData: TrainingModelData;
}

export interface TrainingState {
  status: 'idle' | 'training' | 'paused' | 'completed';
  currentStep: number;
  totalSteps: number;
  loss: number;
  accuracy: number;
  sessionId?: number;
  scoreLabel?: string;
  metrics?: TrainingMetrics;
  modelData?: TrainingModelData;
}
