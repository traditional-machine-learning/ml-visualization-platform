import { Injectable } from '@angular/core';
import { Algorithm, Dataset, TrainingState } from '../models/algorithm.model';

@Injectable({ providedIn: 'root' })
export class ExperimentContextService {
  private algorithm: Algorithm | null = null;
  private dataset: Dataset | null = null;
  private trainingState: TrainingState | null = null;
  private parameters: Record<string, any> = {};

  updateContext(
    algorithm: Algorithm | null,
    dataset: Dataset | null,
    trainingState: TrainingState | null,
    parameters: Record<string, any>
  ): void {
    this.algorithm = algorithm;
    this.dataset = dataset;
    this.trainingState = trainingState;
    this.parameters = parameters;
  }

  getContext(): {
    algorithm: Algorithm | null;
    dataset: Dataset | null;
    trainingState: TrainingState | null;
    parameters: Record<string, any>;
  } {
    return {
      algorithm: this.algorithm,
      dataset: this.dataset,
      trainingState: this.trainingState,
      parameters: this.parameters
    };
  }
}