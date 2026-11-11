import { Algorithm, Dataset, TrainingState } from './algorithm.model';

export interface MetricCard {
  label: string;
  /** 训练尚未开始时为 undefined：卡片只显示标签，不显示任何数字 */
  value?: number;
  suffix?: string;
  display?: 'ratioPercent' | 'percent' | 'number';
  /** 与上一步相比的真实变化量，正负号表示增减；没有上一步时为 undefined（不显示） */
  delta?: number;
  /** 该指标是否越小越好（损失类），用于决定 delta 的红绿方向 */
  lowerIsBetter?: boolean;
  tone: 'cyan' | 'purple' | 'green' | 'amber';
  icon: string;
}

export interface TrainingPoint {
  step: number;
  loss: number;
  score: number;
}

export interface EvaluationPanelState {
  title: string;
  subtitle: string;
  cards: MetricCard[];
  chart: TrainingPoint[];
  scoreLabel: string;
  lossLabel: string;
  /** 训练总步数，用于把训练轨迹的横轴固定为 0..totalSteps */
  totalSteps?: number;
}

export interface ExplanationTreeNode {
  id: string;
  x: number;
  y: number;
  label: string;
  detail: string;
  tone: 'cyan' | 'purple' | 'green';
}

export interface ExplanationTreeEdge {
  from: string;
  to: string;
}

export interface FeatureWeight {
  feature: string;
  value: number;
}

export interface ModelExplanationState {
  mode: 'tree' | 'weights' | 'summary';
  title: string;
  description: string;
  notes: string[];
  treeNodes?: ExplanationTreeNode[];
  treeEdges?: ExplanationTreeEdge[];
  weights?: FeatureWeight[];
}

export interface GuidedExperimentCase {
  id: string;
  title: string;
  summary: string;
  category: string;
  algorithmName: string;
  datasetName: string;
  parameterPreset: Record<string, number | string>;
  objectives: string[];
  validationText: string;
}

export interface ExperimentSimulationState {
  evaluation: EvaluationPanelState;
  explanation: ModelExplanationState;
  statusText: string;
}

export interface LoadedExperimentConfig {
  algorithm: Algorithm;
  dataset: Dataset;
  parameters: Record<string, number | string>;
}

export interface CaseGuidanceState {
  caseId: string | null;
  phase: 'idle' | 'configured' | 'running' | 'verified';
  activeStep: number;
  headline: string;
}

export interface SimulationContext {
  algorithm: Algorithm | null;
  dataset: Dataset | null;
  parameters: Record<string, any>;
  trainingState: TrainingState;
}
