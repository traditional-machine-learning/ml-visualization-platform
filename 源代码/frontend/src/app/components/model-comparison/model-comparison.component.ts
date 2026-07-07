import { Component, OnInit, AfterViewInit, ViewChild, ElementRef, OnDestroy } from '@angular/core';
import * as echarts from 'echarts';
import { ApiService } from '../../services/api.service';
import { Algorithm, Dataset, TrainingSimulationResponse, ParameterDef } from '../../models/algorithm.model';

interface AlgorithmConfig {
  algorithmId?: number;
  alias?: string;
  parameters: { [key: string]: any };
}

@Component({
  selector: 'app-model-comparison',
  templateUrl: './model-comparison.component.html',
  styleUrls: ['./model-comparison.component.scss']
})
export class ModelComparisonComponent implements OnInit {
  algorithms: Algorithm[] = [];
  datasets: Dataset[] = [];
  selectedDatasetId?: number;
  configs: AlgorithmConfig[] = [];
  results: TrainingSimulationResponse[] = [];
  isLoading = false;
  metricToCompare: 'accuracy' | 'loss' = 'accuracy';
  lastSentConfigs: { algorithmId?: number; alias?: string }[] = [];

  @ViewChild('compareChart') compareChartRef?: ElementRef<HTMLDivElement>;
  private compareChart: echarts.ECharts | null = null;

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.getAlgorithms().subscribe(r => { if (r.success) this.algorithms = r.data; });
    this.api.getDatasets().subscribe(r => { if (r.success) this.datasets = r.data; });
  }

  ngAfterViewInit(): void {
    // Chart will be initialized when results are available
  }

  ngOnDestroy(): void {
    this.compareChart?.dispose();
  }

  getAlgorithmName(id: number): string {
    const a = this.algorithms.find(x => x.id === id);
    return a ? (a.displayName || a.name) : String(id);
  }

  getAlgorithmById(id: number): Algorithm | undefined {
    return this.algorithms.find(a => a.id === id);
  }

  addConfig(algorithmId?: number) {
    let idNum: number | undefined;
    if (algorithmId != null) {
      idNum = Number(algorithmId);
    } else if (this.algorithms && this.algorithms.length) {
      idNum = this.algorithms[0].id;
    }
    
    // Initialize parameters with default values
    const algorithm = this.getAlgorithmById(idNum || 0);
    const defaultParams: { [key: string]: any } = {};
    if (algorithm) {
      algorithm.parameters.forEach(param => {
        defaultParams[param.name] = param.default;
      });
    }
    
    this.configs.push({ 
      algorithmId: idNum, 
      parameters: defaultParams,
      alias: undefined 
    });
  }

  removeConfig(index: number) {
    this.configs.splice(index, 1);
  }

  onParameterChange(configIndex: number, paramName: string, value: any) {
    this.configs[configIndex].parameters[paramName] = value;
  }

  formatValue(param: ParameterDef, value: any): string {
    if (param.type === 'number' && typeof value === 'number') {
      if (param.step && param.step < 1) {
        return value.toFixed(param.step.toString().split('.')[1]?.length || 2);
      }
      return value.toString();
    }
    return String(value);
  }

  compare() {
    if (!this.selectedDatasetId || this.configs.length === 0) return;
    this.isLoading = true;
    const dsId = this.selectedDatasetId!;
    
    // prepare items with JSON stringified parameters
    const items = this.configs.map(c => {
      const paramsStr = JSON.stringify(c.parameters);
      return { 
        algorithmId: c.algorithmId || 0, 
        datasetId: dsId, 
        parameters: paramsStr 
      };
    });
    
    // keep a copy of configs to label results appropriately
    this.lastSentConfigs = this.configs.map(c => ({ 
      algorithmId: c.algorithmId, 
      alias: c.alias 
    }));
    
    this.api.compareModels(items).subscribe({ next: res => {
      this.isLoading = false;
      if (res.success) {
        this.results = res.data;
        // Use setTimeout to ensure DOM is updated before rendering chart
        setTimeout(() => {
          this.initAndRenderChart();
        }, 0);
      }
    }, error: () => this.isLoading = false });
  }

  initAndRenderChart(): void {
    // Initialize chart if not already initialized
    if (!this.compareChart && this.compareChartRef?.nativeElement) {
      this.compareChart = echarts.init(this.compareChartRef.nativeElement, undefined, { renderer: 'canvas' });
    }
    this.renderCompareChart();
  }

  renderCompareChart(): void {
    if (!this.compareChart || !this.results.length) return;
    // prefer alias from last sent configs if available, otherwise algorithm name
    const names = this.results.map((r, idx) => {
      const cfg = this.lastSentConfigs && this.lastSentConfigs[idx];
      if (cfg && cfg.alias) return cfg.alias;
      return this.getAlgorithmName(r.algorithmId);
    });
    const values = this.results.map(r => this.metricToCompare === 'accuracy' ? (r.accuracy || 0) : (r.loss || 0));

    this.compareChart.setOption({
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: names },
      yAxis: { type: 'value' },
      series: [{ type: 'bar', data: values, itemStyle: { color: '#06b6d4' } }]
    }, true);
  }
}
