import { Algorithm, Dataset, TrainingState } from './algorithm.model';

export interface MetricCard {
  label: string;
  value: number;
  suffix?: string;
  display?: 'ratioPercent' | 'percent' | 'number';
  trend?: string;
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
