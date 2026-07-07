import { Component, OnInit } from '@angular/core';
import { AiAssistantService } from '../../services/ai-assistant.service';
import { ExperimentContextService } from '../../services/experiment-context.service';
import { ChatMessage, AiAssistantRequest } from '../../models/ai-assistant.model';

@Component({
  selector: 'app-ai-assistant',
  templateUrl: './ai-assistant.component.html',
  styleUrls: ['./ai-assistant.component.scss']
})
export class AiAssistantComponent implements OnInit {
  isMinimized = true;
  messages: ChatMessage[] = [];
  currentQuestion = '';
  isLoading = false;
  messageIdCounter = 0;

  constructor(
    private aiService: AiAssistantService,
    private contextService: ExperimentContextService
  ) {}

  ngOnInit(): void {
    this.messages.push({
      id: this.messageIdCounter++,
      type: 'ai',
      content: '你好！我是AI助手，可以帮助你理解机器学习实验。你可以询问关于算法、训练进度或机器学习概念的问题。',
      timestamp: new Date()
    });
  }

  toggleMinimize(): void {
    this.isMinimized = !this.isMinimized;
  }

  sendQuestion(): void {
    if (!this.currentQuestion.trim() || this.isLoading) {
      return;
    }

    const context = this.contextService.getContext();
    const contextInfo = this.buildContextInfo(context);

    this.messages.push({
      id: this.messageIdCounter++,
      type: 'user',
      content: this.currentQuestion,
      contextInfo: contextInfo,
      timestamp: new Date()
    });

    const request: AiAssistantRequest = {
      question: this.currentQuestion,
      algorithmName: context.algorithm?.name,
      algorithmDisplayName: context.algorithm?.displayName,
      algorithmDescription: context.algorithm?.description,
      datasetName: context.dataset?.name,
      datasetDescription: context.dataset?.description,
      trainingStatus: context.trainingState?.status,
      currentStep: context.trainingState?.currentStep,
      totalSteps: context.trainingState?.totalSteps,
      loss: context.trainingState?.loss,
      accuracy: context.trainingState?.accuracy,
      parametersJson: JSON.stringify(context.parameters)
    };

    this.currentQuestion = '';
    this.isLoading = true;

    this.aiService.askQuestion(request).subscribe({
      next: (response) => {
        this.isLoading = false;
        if (response.success && response.data.success) {
          this.messages.push({
            id: this.messageIdCounter++,
            type: 'ai',
            content: response.data.answer,
            timestamp: new Date()
          });
        } else {
          this.messages.push({
            id: this.messageIdCounter++,
            type: 'ai',
            content: response.data.errorMessage || response.message || '抱歉，发生了错误，请稍后重试。',
            timestamp: new Date()
          });
        }
        this.scrollToBottom();
      },
      error: () => {
        this.isLoading = false;
        this.messages.push({
          id: this.messageIdCounter++,
          type: 'ai',
          content: '抱歉，无法连接到AI服务。请检查后端服务是否正常运行。',
          timestamp: new Date()
        });
        this.scrollToBottom();
      }
    });
  }

  private buildContextInfo(context: any): string {
    const parts: string[] = [];
    if (context.algorithm) {
      parts.push(`算法: ${context.algorithm.displayName}`);
    }
    if (context.dataset) {
      parts.push(`数据集: ${context.dataset.name}`);
    }
    if (context.trainingState && context.trainingState.status !== 'idle') {
      parts.push(`状态: ${context.trainingState.status}`);
    }
    return parts.length > 0 ? parts.join(' | ') : '未选择算法和数据集';
  }

  private scrollToBottom(): void {
    const container = document.querySelector('.messages-container');
    if (container) {
      container.scrollTop = container.scrollHeight;
    }
  }

  clearChat(): void {
    this.messages = [];
    this.ngOnInit();
  }
}