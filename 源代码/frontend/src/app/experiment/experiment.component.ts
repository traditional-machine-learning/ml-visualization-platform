import { Component, OnDestroy, OnInit } from '@angular/core';
import { forkJoin } from 'rxjs';
import { Algorithm, Dataset, TrainingSimulationResponse, TrainingState } from '../models/algorithm.model';
import {
  CaseGuidanceState,
  EvaluationPanelState,
  GuidedExperimentCase,
  ModelExplanationState
} from '../models/learning-guide.model';
import { LearningGuideDataService } from '../services/learning-guide-data.service';
import { ApiService } from '../services/api.service';
import { ExperimentContextService } from '../services/experiment-context.service';
import { environment } from '../../environments/environment';
import { TrainingLogEntry } from '../components/training-log/training-log.component';

@Component({
  selector: 'app-experiment',
  templateUrl: './experiment.component.html',
  styleUrls: ['./experiment.component.scss']
})
export class ExperimentComponent implements OnInit, OnDestroy {
  selectedAlgorithm: Algorithm | null = null;
  selectedDataset: Dataset | null = null;
  parameterValues: Record<string, any> = {};
  trainingState: TrainingState = {
    status: 'idle',
    currentStep: 0,
    totalSteps: this.getTotalSteps(),
    loss: 1.08,
    accuracy: 0.36
  };

  guidedCases: GuidedExperimentCase[] = [];
  activeCaseId: string | null = null;
  guidanceState: CaseGuidanceState = {
    caseId: null,
    phase: 'idle',
    activeStep: 0,
    headline: '加载预设案例以获取引导式工作流程。'
  };
  evaluationState: EvaluationPanelState | null = null;
  explanationState: ModelExplanationState | null = null;
  trainingLog: TrainingLogEntry[] = [];
  statusBanner = '选择算法、数据集或引导式案例以开始。';

  private trainingTimer: ReturnType<typeof setInterval> | null = null;
  private stepInFlight = false;
  private requestVersion = 0;
  private apiAlgorithms: Algorithm[] = [];
  private apiDatasets: Dataset[] = [];
  private catalogLoaded = false;
  useMockFallback = environment.demoMode;

  constructor(
    private learningGuideData: LearningGuideDataService,
    private apiService: ApiService,
    private experimentContext: ExperimentContextService
  ) {}

  ngOnInit(): void {
    this.guidedCases = this.learningGuideData.getGuidedCases();
    const defaultCase = this.guidedCases.find((item) => item.id === 'case-linear-weights') || this.guidedCases[0];
    if (this.useMockFallback) {
      if (defaultCase) {
        this.onLoadCase(defaultCase.id);
        return;
      }
      this.refreshInsightPanels();
      return;
    }

    this.loadExperimentCatalog(defaultCase?.id);
  }

  ngOnDestroy(): void {
    this.clearTrainingTimer();
  }

  getTotalSteps(): number {
    // Get totalSteps from iteration-related parameters
    const iterations = this.parameterValues['iterations'] || this.parameterValues['maxIterations'];
    if (iterations) {
      return Number(iterations);
    }
    // Default based on algorithm type
    if (this.selectedAlgorithm) {
      // For algorithms without iterations param (like decision tree), use depth-based steps
      if (this.selectedAlgorithm.name === 'decision_tree') {
        const maxDepth = this.parameterValues['maxDepth'] || 4;
        return Number(maxDepth) * 2; // Each depth level ~2 steps
      }
    }
    return 8; // Fallback default
  }

  onAlgorithmSelect(algorithm: Algorithm): void {
    this.selectedAlgorithm = algorithm;
    this.parameterValues = this.buildParameterValues(algorithm, {});
    this.trainingState.totalSteps = this.getTotalSteps();
    this.resetTraining(false);
  }

  onDatasetSelect(dataset: Dataset): void {
    this.selectedDataset = dataset;
    this.resetTraining(false);
  }

  onParameterChange(paramName: string, value: any): void {
    this.parameterValues[paramName] = value;
    // Update totalSteps if iterations/maxIterations changed
    this.trainingState.totalSteps = this.getTotalSteps();
    this.refreshInsightPanels();
  }

  onLoadCase(caseId: string): void {
    if (this.useMockFallback) {
      this.applyDemoCase(caseId);
      return;
    }

    if (!this.catalogLoaded) {
      this.loadExperimentCatalog(caseId);
      return;
    }
    this.applyApiCase(caseId);
  }

  private loadExperimentCatalog(caseId?: string): void {
    this.statusBanner = '正在从API加载实验数据。';
    forkJoin({
      algorithms: this.apiService.getAlgorithms(),
      datasets: this.apiService.getDatasets()
    }).subscribe({
      next: ({ algorithms, datasets }) => {
        if (!algorithms.success || !datasets.success) {
          this.handleCatalogError(caseId);
          return;
        }

        this.apiAlgorithms = algorithms.data;
        this.apiDatasets = datasets.data;
        this.catalogLoaded = true;
        this.useMockFallback = false;

        const defaultCase = this.guidedCases.find((item) => item.id === 'case-linear-weights') || this.guidedCases[0];
        const targetCaseId = caseId || defaultCase?.id;
        if (targetCaseId) {
          this.applyApiCase(targetCaseId);
          return;
        }
        this.refreshInsightPanels();
      },
      error: () => this.handleCatalogError(caseId)
    });
  }

  private applyDemoCase(caseId: string): void {
    const loaded = this.learningGuideData.loadCase(caseId);
    this.activeCaseId = caseId;
    this.selectedAlgorithm = loaded.algorithm;
    this.selectedDataset = loaded.dataset;
    this.parameterValues = this.buildParameterValues(loaded.algorithm, loaded.parameters);
    this.resetTraining(false);
  }

  private applyApiCase(caseId: string): void {
    const guidedCase = this.guidedCases.find((item) => item.id === caseId);
    if (!guidedCase) {
      this.statusBanner = `未知的引导式案例：${caseId}。`;
      return;
    }

    const algorithm = this.apiAlgorithms.find((item) => item.name === guidedCase.algorithmName);
    const dataset = this.apiDatasets.find((item) => item.name === guidedCase.datasetName);
    if (!algorithm || !dataset) {
      this.activeCaseId = null;
      this.selectedAlgorithm = null;
      this.selectedDataset = null;
      this.parameterValues = {};
      this.resetTraining(false);
      this.statusBanner = `引导式案例"${guidedCase.title}"缺少API数据。`;
      return;
    }

    this.activeCaseId = caseId;
    this.selectedAlgorithm = algorithm;
    this.selectedDataset = dataset;
    this.parameterValues = this.buildParameterValues(algorithm, guidedCase.parameterPreset);
    this.resetTraining(false);
  }

  private handleCatalogError(caseId?: string): void {
    this.catalogLoaded = false;
    this.apiAlgorithms = [];
    this.apiDatasets = [];
    this.useMockFallback = true;
    const fallbackCase =
      this.guidedCases.find((item) => item.id === caseId)
      || this.guidedCases.find((item) => item.id === 'case-linear-weights')
      || this.guidedCases[0];
    if (fallbackCase) {
      this.applyDemoCase(fallbackCase.id);
      this.statusBanner = 'API不可用。已切换到本地演示数据。';
      return;
    }
    this.activeCaseId = null;
    this.selectedAlgorithm = null;
    this.selectedDataset = null;
    this.parameterValues = {};
    this.resetTraining(false);
    this.statusBanner = 'API不可用。未找到本地演示案例。';
  }

  onStartTraining(): void {
    if (!this.selectedAlgorithm || !this.selectedDataset) {
      return;
    }

    if (this.useMockFallback) {
      this.clearTrainingTimer();
      this.trainingState.status = 'training';
      this.appendTrainingLog('Training started. Parameters are initialized.', 'neutral', this.trainingState.currentStep);
      if (this.trainingState.currentStep >= this.trainingState.totalSteps) {
        this.trainingState.currentStep = 0;
      }
      this.stepTraining();
      this.trainingTimer = setInterval(() => {
        if (this.trainingState.currentStep >= this.trainingState.totalSteps) {
          this.completeTraining();
          return;
        }
        this.stepTraining();
      }, 850);
      return;
    }

    if (this.trainingState.status === 'paused' && this.trainingState.sessionId) {
      this.onResumeTraining();
      return;
    }

    const requestVersion = this.clearTrainingTimer();
    this.appendTrainingLog('Training request sent to backend.', 'neutral', this.trainingState.currentStep);
    this.apiService.startTraining(this.selectedAlgorithm.id, this.selectedDataset.id, this.parameterValues).subscribe({
      next: (response) => {
        if (!this.isCurrentRequest(requestVersion)) {
          return;
        }
        if (!response.success) {
          this.handleTrainingError();
          return;
        }
        this.applyTrainingResponse(response.data);
        const continuousVersion = this.startContinuousTraining();
        this.requestTrainingStep(false, continuousVersion);
      },
      error: () => {
        if (this.isCurrentRequest(requestVersion)) {
          this.handleTrainingError();
        }
      }
    });
  }

  onTrainStep(): void {
    if (!this.selectedAlgorithm || !this.selectedDataset) {
      return;
    }

    if (this.useMockFallback) {
      if (this.trainingState.status === 'completed') {
        this.trainingState.currentStep = 0;
        this.trainingState.status = 'idle';
      }
      this.trainingState.status = 'training';
      this.appendTrainingLog('Single-step update requested.', 'neutral', this.trainingState.currentStep);
      this.stepTraining();
      if (this.trainingState.currentStep >= this.trainingState.totalSteps) {
        this.completeTraining();
      } else {
        this.trainingState.status = 'paused';
      }
      return;
    }

    const requestVersion = this.clearTrainingTimer();
    if (!this.trainingState.sessionId || this.trainingState.status === 'completed') {
      this.apiService.startTraining(this.selectedAlgorithm.id, this.selectedDataset.id, this.parameterValues).subscribe({
        next: (response) => {
          if (!this.isCurrentRequest(requestVersion)) {
            return;
          }
          if (!response.success) {
            this.handleTrainingError();
            return;
          }
          this.applyTrainingResponse(response.data);
          this.requestTrainingStep(true, requestVersion);
        },
        error: () => {
          if (this.isCurrentRequest(requestVersion)) {
            this.handleTrainingError();
          }
        }
      });
      return;
    }

    this.requestTrainingStep(true, requestVersion);
  }

  onPauseTraining(): void {
    if (this.useMockFallback) {
      this.clearTrainingTimer();
      this.trainingState.status = 'paused';
      this.refreshInsightPanels();
      return;
    }

    const requestVersion = this.clearTrainingTimer();
    if (!this.selectedAlgorithm || !this.trainingState.sessionId) {
      this.trainingState.status = 'paused';
      this.refreshInsightPanels();
      return;
    }

    const sessionId = this.trainingState.sessionId;
    this.apiService.pauseTraining(this.selectedAlgorithm.id, this.trainingState.sessionId).subscribe({
      next: (response) => {
        if (!this.isCurrentRequest(requestVersion, sessionId)) {
          return;
        }
        if (!response.success) {
          this.handleTrainingError();
          return;
        }
        this.applyTrainingResponse(response.data);
      },
      error: () => {
        if (this.isCurrentRequest(requestVersion, sessionId)) {
          this.handleTrainingError();
        }
      }
    });
  }

  onResumeTraining(): void {
    if (!this.selectedAlgorithm || !this.selectedDataset) {
      return;
    }

    if (this.useMockFallback) {
      this.onStartTraining();
      return;
    }

    if (!this.trainingState.sessionId) {
      this.onStartTraining();
      return;
    }
    this.trainingState.status = 'training';
    this.appendTrainingLog('Continuous training resumed.', 'neutral', this.trainingState.currentStep);
    this.refreshInsightPanels();
    const requestVersion = this.startContinuousTraining();
    this.requestTrainingStep(false, requestVersion);
  }

  resetTraining(resetSelections = false): void {
    this.clearTrainingTimer();
    this.trainingState = {
      status: 'idle',
      currentStep: 0,
      totalSteps: this.getTotalSteps(),
      loss: 1.08,
      accuracy: 0.36,
      scoreLabel: 'Accuracy'
    };
    if (resetSelections) {
      this.selectedAlgorithm = null;
      this.selectedDataset = null;
      this.parameterValues = {};
      this.activeCaseId = null;
    }
    this.resetTrainingLog();
    this.refreshInsightPanels();
  }

  private completeTraining(): void {
    this.clearTrainingTimer();
    this.trainingState.status = 'completed';
    this.appendTrainingLog('Training completed. Metrics and explanations are ready.', 'success', this.trainingState.currentStep);
    this.refreshInsightPanels();
  }

  private stepTraining(): void {
    const nextStep = Math.min(this.trainingState.totalSteps, this.trainingState.currentStep + 1);
    const progress = nextStep / this.trainingState.totalSteps;
    this.trainingState = {
      ...this.trainingState,
      status: 'training',
      currentStep: nextStep,
      loss: Number((1.08 - progress * 0.72).toFixed(3)),
      accuracy: Number((0.36 + progress * 0.56).toFixed(3))
    };
    this.appendProgressLog(progress, this.trainingState.currentStep);
    this.refreshInsightPanels();
  }

  private startContinuousTraining(): number {
    const requestVersion = this.clearTrainingTimer();
    this.trainingState.status = 'training';
    this.refreshInsightPanels();
    this.trainingTimer = setInterval(() => {
      this.requestTrainingStep(false, requestVersion);
    }, 850);
    return requestVersion;
  }

  private requestTrainingStep(singleStep: boolean, requestVersion = this.requestVersion): void {
    if (!this.selectedAlgorithm || !this.selectedDataset || !this.trainingState.sessionId || this.stepInFlight) {
      return;
    }

    if (this.trainingState.currentStep >= this.trainingState.totalSteps) {
      this.completeTraining();
      return;
    }

    this.stepInFlight = true;
    const sessionId = this.trainingState.sessionId;
    this.apiService.trainStep(
      this.selectedAlgorithm.id,
      sessionId,
      this.selectedDataset.id,
      this.parameterValues
    ).subscribe({
      next: (response) => {
        if (!this.isCurrentRequest(requestVersion, sessionId)) {
          return;
        }
        this.stepInFlight = false;
        if (!response.success) {
          this.handleTrainingError();
          return;
        }

        this.applyTrainingResponse(response.data);
        if (response.data.status === 'completed' || response.data.currentStep >= response.data.totalSteps) {
          this.completeTraining();
          return;
        }

        if (singleStep) {
          this.trainingState = {
            ...this.trainingState,
            status: 'paused'
          };
          this.refreshInsightPanels();
        }
      },
      error: () => {
        if (!this.isCurrentRequest(requestVersion, sessionId)) {
          return;
        }
        this.stepInFlight = false;
        this.handleTrainingError();
      }
    });
  }

  private applyTrainingResponse(response: TrainingSimulationResponse): void {
    // Keep the totalSteps from parameters, only use API response if it matches
    const totalSteps = this.getTotalSteps();
    this.trainingState = {
      ...this.trainingState,
      sessionId: response.sessionId,
      status: this.normalizeStatus(response.status),
      currentStep: response.currentStep,
      totalSteps: totalSteps, // Use parameter-based totalSteps instead of API default
      loss: response.loss,
      accuracy: response.accuracy,
      scoreLabel: response.scoreLabel,
      metrics: response.metrics,
      modelData: response.modelData
    };
    this.refreshInsightPanels();
  }

  private normalizeStatus(status: string): TrainingState['status'] {
    if (status === 'training' || status === 'paused' || status === 'completed' || status === 'idle') {
      return status;
    }
    return 'idle';
  }

  private buildParameterValues(
    algorithm: Algorithm,
    overrides: Record<string, number | string>
  ): Record<string, any> {
    const values: Record<string, any> = {};
    (algorithm.parameters || []).forEach((param) => {
      values[param.name] = param.default;
    });
    return { ...values, ...overrides };
  }

  private handleTrainingError(): void {
    this.clearTrainingTimer();
    if (this.trainingState.status === 'training') {
      this.trainingState = {
        ...this.trainingState,
        status: this.trainingState.currentStep > 0 ? 'paused' : 'idle'
      };
    }
    this.refreshInsightPanels();
    this.statusBanner = '训练请求失败。请检查后端服务并重试。';
  }

  private clearTrainingTimer(): number {
    if (this.trainingTimer) {
      clearInterval(this.trainingTimer);
      this.trainingTimer = null;
    }
    this.stepInFlight = false;
    this.requestVersion += 1;
    return this.requestVersion;
  }

  private isCurrentRequest(requestVersion: number, sessionId?: number): boolean {
    if (requestVersion !== this.requestVersion) {
      return false;
    }
    return sessionId === undefined || this.trainingState.sessionId === sessionId;
  }

  private refreshInsightPanels(): void {
    const simulation = this.learningGuideData.buildSimulation({
      algorithm: this.selectedAlgorithm,
      dataset: this.selectedDataset,
      parameters: this.parameterValues,
      trainingState: this.trainingState
    });

    this.evaluationState = simulation.evaluation;
    this.explanationState = simulation.explanation;
    this.statusBanner = simulation.statusText;
    this.guidanceState = this.learningGuideData.buildGuidanceState(this.activeCaseId, this.trainingState);

    // Update context for AI assistant
    this.experimentContext.updateContext(
      this.selectedAlgorithm,
      this.selectedDataset,
      this.trainingState,
      this.parameterValues
    );
  }

  private resetTrainingLog(): void {
    this.trainingLog = this.selectedAlgorithm && this.selectedDataset
      ? [{
          step: 0,
          text: `Preset ready: ${this.selectedAlgorithm.displayName} on ${this.selectedDataset.name}.`,
          tone: 'neutral'
        }]
      : [];
  }

  private appendProgressLog(progress: number, step: number): void {
    if (step <= 0) {
      return;
    }

    if (step === 1) {
      this.appendTrainingLog('First update received. Loss starts moving from the baseline.', 'neutral', step);
      return;
    }

    if (progress >= 1) {
      return;
    }

    if (progress >= 0.7 && !this.trainingLog.some((entry) => entry.text.includes('stabilizing'))) {
      this.appendTrainingLog('Boundary and metrics are stabilizing. The model is close to convergence.', 'success', step);
      return;
    }

    if (progress >= 0.35 && !this.trainingLog.some((entry) => entry.text.includes('clearer'))) {
      this.appendTrainingLog('Loss is dropping and the score trend is getting clearer.', 'success', step);
    }
  }

  private appendTrainingLog(text: string, tone: TrainingLogEntry['tone'], step: number): void {
    this.trainingLog = [
      ...this.trainingLog,
      { step, text, tone }
    ].slice(-8);
  }
}
