import {
  AfterViewInit,
  Component,
  ElementRef,
  HostListener,
  Input,
  OnChanges,
  OnDestroy,
  SimpleChanges,
  ViewChild
} from '@angular/core';
import * as echarts from 'echarts';
import { Algorithm, Dataset, TrainingState } from '../../models/algorithm.model';

@Component({
  selector: 'app-visualization-canvas',
  templateUrl: './visualization-canvas.component.html',
  styleUrls: ['./visualization-canvas.component.scss']
})
export class VisualizationCanvasComponent implements AfterViewInit, OnChanges, OnDestroy {
  @Input() dataset: Dataset | null = null;
  @Input() algorithm: Algorithm | null = null;
  @Input() trainingState: TrainingState | null = null;

  @ViewChild('chartHost')
  set chartHostRef(host: ElementRef<HTMLDivElement> | undefined) {
    if (!host && this.chart) {
      this.chart.dispose();
      this.chart = null;
    }
    this.chartHost = host;
    this.renderChart();
  }

  @ViewChild('importanceHost')
  set importanceHostRef(host: ElementRef<HTMLDivElement> | undefined) {
    if (!host && this.importanceChart) {
      this.importanceChart.dispose();
      this.importanceChart = null;
    }
    this.importanceHost = host;
    this.renderImportanceChart();
  }

  private chartHost?: ElementRef<HTMLDivElement>;
  private chart: echarts.ECharts | null = null;
  private importanceHost?: ElementRef<HTMLDivElement>;
  private importanceChart: echarts.ECharts | null = null;

  ngAfterViewInit(): void {
    this.renderChart();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['dataset'] || changes['algorithm'] || changes['trainingState']) {
      this.renderChart();
    }
  }

  ngOnDestroy(): void {
    this.chart?.dispose();
  }

  @HostListener('window:resize')
  onResize(): void {
    this.chart?.resize();
  }

  getUniqueLabels(): string[] {
    if (!this.dataset?.dataPoints?.length) {
      return [];
    }

    const labels = this.dataset.dataPoints.map((point, index) => this.resolvePointLabel(point.label, point.clusterId, index));
    return [...new Set(labels)];
  }

  getLegendColor(label: string): string {
    return this.getColorMap()[label] || '#378ADD';
  }

  private renderChart(): void {
    if (!this.chartHost?.nativeElement || !this.dataset) {
      return;
    }

    if (!this.chart) {
      this.chart = echarts.init(this.chartHost.nativeElement, undefined, { renderer: 'canvas' });
    }

    // If PCA transformed data exists, use it as scatter source
    const modelData = this.trainingState?.modelData;
    let datasets: any[] = [];

    if (modelData?.pcaTransformed && modelData.pcaTransformed.length) {
      const transformed = modelData.pcaTransformed as number[][];
      const points = transformed.map((p, i) => [p[0], p[1]]);
      datasets = [{
        name: 'PCA投影',
        type: 'scatter',
        symbolSize: 7,
        data: points,
        itemStyle: {
          color: this.getLegendColor('Samples'),
          borderColor: 'rgba(255,255,255,0.45)',
          borderWidth: 1,
          shadowBlur: 5,
          shadowColor: `${this.getLegendColor('Samples')}50`
        }
      }];
    } else {
      const grouped = this.groupPoints();
      datasets = Object.entries(grouped).map(([label, points]) => ({
      name: label,
      type: 'scatter',
      symbolSize: 7,
      data: points,
      itemStyle: {
        color: this.getLegendColor(label),
        borderColor: 'rgba(255,255,255,0.45)',
        borderWidth: 1,
        shadowBlur: 5,
        shadowColor: `${this.getLegendColor(label)}50`
      },
      emphasis: {
        itemStyle: {
          borderColor: '#ffffff',
          borderWidth: 1.5,
          shadowBlur: 12,
          shadowColor: `${this.getLegendColor(label)}80`
        }
      }
    }));
    }

    const overlaySeries = this.getOverlaySeries();

    this.chart.setOption({
      animation: true,
      animationDuration: 650,
      animationDurationUpdate: 520,
      animationEasing: 'cubicOut',
      animationDelay: (index: number) => index * 18,
      tooltip: {
        trigger: 'item',
        backgroundColor: '#111827',
        borderColor: 'rgba(255,255,255,0.08)',
        textStyle: {
          color: '#E2E8F0'
        },
        formatter: (params: any) => {
          if (Array.isArray(params.value)) {
            return `${params.seriesName}<br/>x: ${params.value[0]}<br/>y: ${params.value[1]}`;
          }
          return params.seriesName;
        }
      },
      grid: {
        left: 56,
        right: 24,
        top: 26,
        bottom: 52
      },
      xAxis: {
        type: 'value',
        name: this.dataset.features[0]?.displayName || 'Feature X',
        nameLocation: 'middle',
        nameGap: 34,
        nameTextStyle: { color: '#1e293b', fontWeight: 600, fontSize: 11 },
        axisLine: { lineStyle: { color: '#94A3B8' } },
        axisLabel: { color: '#334155', fontSize: 10, fontWeight: 500 },
        splitLine: { lineStyle: { color: 'rgba(15,23,42,0.06)' } }
      },
      yAxis: {
        type: 'value',
        name: this.dataset.features[1]?.displayName || 'Feature Y',
        nameLocation: 'middle',
        nameGap: 44,
        nameTextStyle: { color: '#1e293b', fontWeight: 600, fontSize: 11 },
        axisLine: { lineStyle: { color: '#94A3B8' } },
        axisLabel: { color: '#334155', fontSize: 10, fontWeight: 500 },
        splitLine: { lineStyle: { color: 'rgba(15,23,42,0.06)' } }
      },
      series: [...datasets, ...overlaySeries]
    }, true);

    // Render importance chart if present
    this.renderImportanceChart();
    // ensure chart sizes correctly
    setTimeout(() => this.chart?.resize(), 50);
  }

  private renderImportanceChart(): void {
    if (!this.importanceHost?.nativeElement) {
      return;
    }

    const importances = this.trainingState?.modelData?.featureImportances;
    if (!importances || !importances.length) {
      if (this.importanceChart) {
        this.importanceChart.dispose();
        this.importanceChart = null;
      }
      return;
    }

    if (!this.importanceChart) {
      this.importanceChart = echarts.init(this.importanceHost.nativeElement, undefined, { renderer: 'canvas' });
    }

    const names = importances.map(i => i.feature);
    const values = importances.map(i => i.importance);

    this.importanceChart.setOption({
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      xAxis: { type: 'category', data: names, axisLabel: { rotate: 45 } },
      yAxis: { type: 'value' },
      grid: { left: 12, right: 12, bottom: 40, top: 10 },
      series: [{ type: 'bar', data: values, itemStyle: { color: '#38BDF8', borderRadius: [3, 3, 0, 0] } }]
    }, true);
    setTimeout(() => this.importanceChart?.resize(), 50);
  }

  private groupPoints(): Record<string, number[][]> {
    const grouped: Record<string, number[][]> = {};

    this.dataset?.dataPoints.forEach((point, index) => {
      const key = this.resolvePointLabel(point.label, point.clusterId, index);
      if (!grouped[key]) {
        grouped[key] = [];
      }
      grouped[key].push([point.x, point.y]);
    });

    return grouped;
  }

  private resolvePointLabel(label?: string, clusterId?: number, index?: number): string {
    const assignment = this.resolveAssignment(index);
    if (assignment !== undefined) {
      return `聚类 ${assignment + 1}`;
    }
    if (label) {
      return label;
    }
    if (clusterId !== undefined) {
      return `聚类 ${clusterId + 1}`;
    }
    return 'Samples';
  }

  private resolveAssignment(index?: number): number | undefined {
    const assignments = this.trainingState?.modelData?.assignments;
    if (index === undefined || !assignments || assignments.length !== this.dataset?.dataPoints?.length) {
      return undefined;
    }
    return assignments[index];
  }

  private getColorMap(): Record<string, string> {
    return {
      setosa: '#38BDF8',
      versicolor: '#FB923C',
      virginica: '#34D399',
      '0': '#38BDF8',
      '1': '#F472B6',
      '2': '#34D399',
      '3': '#FB923C',
      '聚类 1': '#38BDF8',
      '聚类 2': '#F472B6',
      '聚类 3': '#34D399',
      '聚类 4': '#FB923C',
      Samples: '#38BDF8'
    };
  }

  private getOverlaySeries(): any[] {
    if (!this.dataset?.dataPoints?.length || !this.algorithm) {
      return [];
    }

    const modelData = this.trainingState?.modelData;
    const fallbackLine = this.getFallbackLine();
    const decisionBoundary = modelData?.decisionBoundary;
    const regressionLine = modelData?.regressionLine;

    if (this.algorithm.type === 'classification') {
      return [{
        name: '决策边界',
        type: 'line',
        data: this.isLineData(decisionBoundary) ? decisionBoundary : fallbackLine,
        symbol: 'none',
        lineStyle: {
          color: 'rgba(0, 0, 0, 0.55)',
          width: 1.5,
          type: 'dashed'
        },
        z: 10
      }];
    }

    if (this.algorithm.type === 'regression') {
      return [{
        name: '回归线',
        type: 'line',
        data: this.isLineData(regressionLine) ? regressionLine : fallbackLine,
        symbol: 'none',
        lineStyle: {
          color: 'rgba(0, 0, 0, 0.65)',
          width: 1.5
        },
        z: 10
      }];
    }

    if (this.algorithm.type === 'clustering') {
      const centroids = modelData?.centroids?.length
        ? modelData.centroids.map((centroid) => [centroid.x, centroid.y, centroid.clusterId])
        : this.getFallbackCentroids();

      return [{
        name: '聚类中心',
        type: 'scatter',
        symbol: 'diamond',
        symbolSize: 14,
        data: centroids,
        itemStyle: {
          color: 'rgba(255,255,255,0.92)',
          borderColor: 'rgba(15,23,42,0.35)',
          borderWidth: 1.5,
          shadowBlur: 10,
          shadowColor: 'rgba(0,0,0,0.25)'
        },
        label: {
          show: true,
          formatter: (params: any) => `C${Number(params.value[2]) + 1}`,
          color: '#94A3B8',
          fontWeight: 600,
          fontSize: 10,
          position: 'top',
          distance: 6
        },
        z: 20
      }];
    }

    return [];
  }

  private getFallbackLine(): number[][] {
    if (!this.dataset?.dataPoints?.length) {
      return [];
    }

    const progress = this.getTrainingProgress();
    const xValues = this.dataset.dataPoints.map((point) => point.x);
    const yValues = this.dataset.dataPoints.map((point) => point.y);
    const minX = Math.min(...xValues);
    const maxX = Math.max(...xValues);
    const minY = Math.min(...yValues);
    const maxY = Math.max(...yValues);
    const avgY = yValues.reduce((sum, value) => sum + value, 0) / yValues.length;
    const slope = this.algorithm?.type === 'classification'
      ? -0.5 + progress * 0.9
      : ((maxY - minY) / Math.max(1, maxX - minX)) * (0.15 + progress * 0.85);
    const shift = this.algorithm?.type === 'classification' ? 0.5 - progress * 0.8 : 0;
    const midX = (minX + maxX) / 2;

    return [
      [minX, avgY + (minX - midX) * slope + shift],
      [maxX, avgY + (maxX - midX) * slope + shift]
    ];
  }

  private getFallbackCentroids(): number[][] {
    if (!this.dataset?.dataPoints?.length) {
      return [];
    }

    const progress = this.getTrainingProgress();
    const xValues = this.dataset.dataPoints.map((point) => point.x);
    const yValues = this.dataset.dataPoints.map((point) => point.y);
    const minX = Math.min(...xValues);
    const maxX = Math.max(...xValues);
    const minY = Math.min(...yValues);
    const maxY = Math.max(...yValues);
    const avgX = xValues.reduce((sum, value) => sum + value, 0) / xValues.length;
    const avgY = yValues.reduce((sum, value) => sum + value, 0) / yValues.length;
    const clusterCount = Math.max(2, Math.min(4, this.getUniqueLabels().length || 3));

    return Array.from({ length: clusterCount }, (_, index) => {
      const targetX = minX + ((index + 1) / (clusterCount + 1)) * (maxX - minX);
      const targetY = minY + (((index % 2) + 1) / 3) * (maxY - minY);
      const startX = avgX + (index - (clusterCount - 1) / 2) * (maxX - minX) * 0.08;
      const startY = avgY + (index % 2 === 0 ? -1 : 1) * (maxY - minY) * 0.08;

      return [
        startX + (targetX - startX) * progress,
        startY + (targetY - startY) * progress,
        index
      ];
    });
  }

  private getTrainingProgress(): number {
    if (!this.trainingState?.totalSteps) {
      return 0;
    }
    return Math.max(0, Math.min(1, this.trainingState.currentStep / this.trainingState.totalSteps));
  }

  private isLineData(data?: number[][]): data is number[][] {
    return !!data && data.length >= 2 && data.every((point) => point.length >= 2);
  }
}
