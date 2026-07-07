import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  ApiResponse,
  Dataset,
  Algorithm,
  TrainingMetrics,
  TrainingModelData,
  TrainingSimulationResponse
} from '../models/algorithm.model';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class ApiService {
  private baseUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  // Dataset endpoints
  getDatasets(): Observable<ApiResponse<Dataset[]>> {
    return this.http.get<ApiResponse<Dataset[]>>(`${this.baseUrl}/datasets`);
  }

  getDatasetById(id: number): Observable<ApiResponse<Dataset>> {
    return this.http.get<ApiResponse<Dataset>>(`${this.baseUrl}/datasets/${id}`);
  }

  getDatasetsByCategory(category: string): Observable<ApiResponse<Dataset[]>> {
    return this.http.get<ApiResponse<Dataset[]>>(`${this.baseUrl}/datasets/category/${category}`);
  }

  // Algorithm endpoints
  getAlgorithms(): Observable<ApiResponse<Algorithm[]>> {
    return this.http.get<ApiResponse<Algorithm[]>>(`${this.baseUrl}/algorithms`);
  }

  getAlgorithmById(id: number): Observable<ApiResponse<Algorithm>> {
    return this.http.get<ApiResponse<Algorithm>>(`${this.baseUrl}/algorithms/${id}`);
  }

  getAlgorithmsByCategory(category: string): Observable<ApiResponse<Algorithm[]>> {
    return this.http.get<ApiResponse<Algorithm[]>>(`${this.baseUrl}/algorithms/category/${category}`);
  }

  startTraining(
    algorithmId: number,
    datasetId: number,
    parameters: any
  ): Observable<ApiResponse<TrainingSimulationResponse>> {
    return this.http.post<ApiResponse<TrainingSimulationResponse>>(`${this.baseUrl}/algorithms/${algorithmId}/train`, {
      datasetId,
      parameters: JSON.stringify(parameters)
    });
  }

  trainStep(
    algorithmId: number,
    sessionId: number,
    datasetId: number,
    parameters: any
  ): Observable<ApiResponse<TrainingSimulationResponse>> {
    return this.http.post<ApiResponse<TrainingSimulationResponse>>(`${this.baseUrl}/algorithms/${algorithmId}/step`, {
      sessionId,
      datasetId,
      parameters: JSON.stringify(parameters)
    });
  }

  pauseTraining(algorithmId: number, sessionId: number): Observable<ApiResponse<TrainingSimulationResponse>> {
    return this.http.post<ApiResponse<TrainingSimulationResponse>>(`${this.baseUrl}/algorithms/${algorithmId}/pause`, {
      sessionId
    });
  }

  startPca(algorithmId: number, datasetId: number, parameters: any): Observable<ApiResponse<TrainingSimulationResponse>> {
    return this.http.post<ApiResponse<TrainingSimulationResponse>>(`${this.baseUrl}/algorithms/${algorithmId}/train-pca`, {
      datasetId,
      parameters: JSON.stringify(parameters)
    });
  }

  startRandomForest(algorithmId: number, datasetId: number, parameters: any): Observable<ApiResponse<TrainingSimulationResponse>> {
    return this.http.post<ApiResponse<TrainingSimulationResponse>>(`${this.baseUrl}/algorithms/${algorithmId}/train-rf`, {
      datasetId,
      parameters: JSON.stringify(parameters)
    });
  }

  compareModels(items: { algorithmId: number; datasetId: number; parameters?: string }[]): Observable<ApiResponse<TrainingSimulationResponse[]>> {
    return this.http.post<ApiResponse<TrainingSimulationResponse[]>>(`${this.baseUrl}/comparison`, { items });
  }

  selectFeatures(algorithmId: number, datasetId: number, selectedFeatures: string[], parameters?: any): Observable<ApiResponse<TrainingSimulationResponse>> {
    return this.http.post<ApiResponse<TrainingSimulationResponse>>(`${this.baseUrl}/feature-engineering/select`, {
      algorithmId,
      datasetId,
      selectedFeatures,
      parameters: JSON.stringify(parameters || {})
    });
  }

  // Visualization endpoints
  getDataPoints(datasetId: number): Observable<ApiResponse<any[]>> {
    return this.http.get<ApiResponse<any[]>>(`${this.baseUrl}/visualization/data-points/${datasetId}`);
  }

  getDecisionBoundary(sessionId: number): Observable<ApiResponse<TrainingModelData>> {
    return this.http.get<ApiResponse<TrainingModelData>>(`${this.baseUrl}/visualization/decision-boundary/${sessionId}`);
  }

  getTrainingMetrics(sessionId: number): Observable<ApiResponse<TrainingMetrics>> {
    return this.http.get<ApiResponse<TrainingMetrics>>(`${this.baseUrl}/visualization/training-metrics/${sessionId}`);
  }
}
