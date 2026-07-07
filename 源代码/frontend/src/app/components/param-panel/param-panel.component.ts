import { Component, Input, Output, EventEmitter } from '@angular/core';
import { Algorithm, ParameterDef } from '../../models/algorithm.model';

@Component({
  selector: 'app-param-panel',
  templateUrl: './param-panel.component.html',
  styleUrls: ['./param-panel.component.scss']
})
export class ParamPanelComponent {
  @Input() algorithm: Algorithm | null = null;
  @Input() parameterValues: any = {};
  @Output() parameterChange = new EventEmitter<{ paramName: string, value: any }>();

  onNumberChange(paramName: string, event: any) {
    this.parameterChange.emit({
      paramName,
      value: parseFloat(event.target.value)
    });
  }

  onSelectChange(paramName: string, value: string) {
    this.parameterChange.emit({
      paramName,
      value
    });
  }

  onSliderChange(paramName: string, value: number) {
    this.parameterChange.emit({
      paramName,
      value
    });
  }

  formatValue(param: ParameterDef, value: number): string {
    if (param.step && param.step < 1) {
      return value.toFixed(param.step.toString().split('.')[1]?.length || 2);
    }
    return value.toString();
  }

  getSliderStep(param: ParameterDef): number {
    return param.step || 1;
  }
}