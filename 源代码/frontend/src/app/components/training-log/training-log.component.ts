import { Component, Input } from '@angular/core';

export interface TrainingLogEntry {
  step: number;
  text: string;
  tone: 'neutral' | 'success' | 'warning';
}

@Component({
  selector: 'app-training-log',
  templateUrl: './training-log.component.html',
  styleUrls: ['./training-log.component.scss']
})
export class TrainingLogComponent {
  @Input() entries: TrainingLogEntry[] = [];
  @Input() status: string = 'idle';
}
