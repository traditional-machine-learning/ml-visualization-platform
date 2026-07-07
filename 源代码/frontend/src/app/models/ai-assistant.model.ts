export interface AiAssistantRequest {
  question: string;
  algorithmName?: string;
  algorithmDisplayName?: string;
  algorithmDescription?: string;
  datasetName?: string;
  datasetDescription?: string;
  trainingStatus?: string;
  currentStep?: number;
  totalSteps?: number;
  loss?: number;
  accuracy?: number;
  parametersJson?: string;
}

export interface AiAssistantResponse {
  answer: string;
  success: boolean;
  errorMessage?: string;
}

export interface ChatMessage {
  id: number;
  type: 'user' | 'ai';
  content: string;
  contextInfo?: string;
  timestamp: Date;
}