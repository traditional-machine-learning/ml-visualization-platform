import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '../models/algorithm.model';
import { AiAssistantRequest, AiAssistantResponse } from '../models/ai-assistant.model';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class AiAssistantService {
  private baseUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  askQuestion(request: AiAssistantRequest): Observable<ApiResponse<AiAssistantResponse>> {
    return this.http.post<ApiResponse<AiAssistantResponse>>(
      `${this.baseUrl}/ai-assistant/ask`,
      request
    );
  }
}