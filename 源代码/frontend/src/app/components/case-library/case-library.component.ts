import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CaseGuidanceState, GuidedExperimentCase } from '../../models/learning-guide.model';

@Component({
  selector: 'app-case-library',
  templateUrl: './case-library.component.html',
  styleUrls: ['./case-library.component.scss']
})
export class CaseLibraryComponent {
  @Input() cases: GuidedExperimentCase[] = [];
  @Input() activeCaseId: string | null = null;
  @Input() guidance!: CaseGuidanceState;
  @Output() loadCase = new EventEmitter<string>();

  readonly phaseLabels: string[] = [
    '加载预设',
    '检查设置',
    '运行训练',
    '查看指标',
    '验证结果'
  ];

  isActive(caseId: string): boolean {
    return this.activeCaseId === caseId;
  }

  getValidationText(): string {
    return this.cases.find((item) => item.id === this.activeCaseId)?.validationText || '';
  }
}
