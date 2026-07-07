export interface Dataset {
  id: number;
  name: string;
  description: string;
  category: string;
  featureCount: number;
  sampleCount: number;
  features: FeatureInfo[];
  dataPoints: DataPoint[];
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
}

export interface TrainingModelData {
  decisionBoundary?: number[][];
  regressionLine?: number[][];
  centroids?: TrainingCentroid[];
  assignments?: number[];
  pcaComponents?: number[][];
  pcaTransformed?: number[][];
  featureImportances?: FeatureImportance[];
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
