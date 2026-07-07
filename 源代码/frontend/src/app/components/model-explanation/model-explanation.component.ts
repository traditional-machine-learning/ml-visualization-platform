import { Component, Input } from '@angular/core';
import { Algorithm } from '../../models/algorithm.model';
import { ModelExplanationState } from '../../models/learning-guide.model';

@Component({
  selector: 'app-model-explanation',
  templateUrl: './model-explanation.component.html',
  styleUrls: ['./model-explanation.component.scss']
})
export class ModelExplanationComponent {
  @Input() algorithm: Algorithm | null = null;
  @Input() explanation: ModelExplanationState | null = null;

  getNodeX(nodeId: string): number {
    return this.explanation?.treeNodes?.find((node) => node.id === nodeId)?.x || 0;
  }

  getNodeY(nodeId: string): number {
    return this.explanation?.treeNodes?.find((node) => node.id === nodeId)?.y || 0;
  }

  getWeightWidth(value: number): number {
    return Math.max(8, Math.abs(value) * 100);
  }
}
