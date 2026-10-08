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
import { Algorithm, DataPoint, Dataset, FeatureInfo, TrainingState } from '../../models/algorithm.model';

@Component({
  selector: 'app-visualization-canvas',
  templateUrl: './visualization-canvas.component.html',
  styleUrls: ['./visualization-canvas.component.scss']
})
export class VisualizationCanvasComponent implements AfterViewInit, OnChanges, OnDestroy {
  @Input() dataset: Dataset | null = null;
  @Input() algorithm: Algorithm | null = null;
  @Input() trainingState: TrainingState | null = null;

  /** 坐标轴可选维度（来自数据集详情接口），以及当前选中的两个维度名 */
  axisOptions: FeatureInfo[] = [];
  selectedXName: string | null = null;
  selectedYName: string | null = null;
  private currentDatasetId: number | null = null;

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
    if (changes['dataset']) {
      // 数据集可能先后到达两次：列表接口的投影版本，随后是详情接口的全维度版本
      this.syncAxisSelection(changes['dataset'].currentValue);
    }
    if (changes['dataset'] || changes['algorithm'] || changes['trainingState']) {
      this.renderChart();
    }
  }

  // ==================== 坐标轴选择 ====================

  get isPcaView(): boolean {
    return !!this.trainingState?.modelData?.pcaTransformed?.length;
  }

  /** 当前两轴是否就是训练所用的投影维度（只有此时叠加线才有意义） */
  get isProjectionView(): boolean {
    const projectedX = this.dataset?.features?.[0]?.name ?? null;
    const projectedY = this.dataset?.features?.[1]?.name ?? null;
    if (!projectedX || !projectedY) {
      return true;
    }
    return this.selectedXName === projectedX && this.selectedYName === projectedY;
  }

  get pickersEnabled(): boolean {
    return this.axisOptions.length >= 2;
  }

  get showAxisHint(): boolean {
    return !this.isPcaView && !this.isProjectionView && !!this.algorithm;
  }

  /** 训练实际使用的两个维度，用于提示文案 */
  get projectedAxesLabel(): string {
    const x = this.getAxisDisplayName(this.dataset?.features?.[0]?.name ?? null, 'X');
    const y = this.getAxisDisplayName(this.dataset?.features?.[1]?.name ?? null, 'Y');
    return `${x} / ${y}`;
  }

  onAxisChange(axis: 'x' | 'y', dimName: string): void {
    if (axis === 'x') {
      this.selectedXName = dimName;
    } else {
      this.selectedYName = dimName;
    }
    this.renderChart();
  }

  /** 数据集变化时重建可选维度并维护默认选中项 */
  private syncAxisSelection(dataset: Dataset | null): void {
    if (!dataset) {
      this.axisOptions = [];
      this.selectedXName = null;
      this.selectedYName = null;
      this.currentDatasetId = null;
      return;
    }

    const options = this.buildAxisOptions(dataset);
    const names = new Set(options.map((dim) => dim.name));
    const defaultX = dataset.features?.[0]?.name ?? null;
    const defaultY = dataset.features?.[1]?.name ?? null;

    this.axisOptions = options;

    if (dataset.id !== this.currentDatasetId) {
      this.currentDatasetId = dataset.id;
      this.selectedXName = defaultX;
      this.selectedYName = defaultY;
      return;
    }

    // 同一数据集的新对象（详情数据到达）：选中项仍存在就保留，否则回到投影的两轴
    if (!this.selectedXName || !names.has(this.selectedXName)) {
      this.selectedXName = defaultX;
    }
    if (!this.selectedYName || !names.has(this.selectedYName)) {
      this.selectedYName = defaultY;
    }
  }

  /**
   * 可选维度 = 全部非 label 维度 + 当前投影的两轴。
   * 后者的补充是必需的：boston 的投影 Y 就是 label 类型的 medv，
   * 不补上会出现"正在绘制的维度在下拉框里找不到"。
   */
  private buildAxisOptions(dataset: Dataset): FeatureInfo[] {
    const declared = dataset.dimensions?.length ? dataset.dimensions : (dataset.features ?? []);
    const options = declared.filter((dim) => dim.type !== 'label');

    for (const projected of dataset.features ?? []) {
      if (!options.some((dim) => dim.name === projected.name)) {
        options.push(projected);
      }
    }

    return options.length ? options : (dataset.features ?? []);
  }

  private getAxisDisplayName(dimName: string | null, fallback: string): string {
    if (!dimName) {
      return fallback;
    }
    const matched = this.axisOptions.find((dim) => dim.name === dimName)
      ?? this.dataset?.features?.find((dim) => dim.name === dimName);
    return matched?.displayName || dimName;
  }

  /**
   * 取某点在指定维度上的值。投影维度直接读 point.x / point.y：
   * 既保证默认视图与改动前完全一致，也避开后端投影时的兜底替换
   * （那里 point.x 可能取自别的列，与 values[xName] 并不一致）。
   */
  private getPointAxisValue(point: DataPoint, dimName: string | null, index: 0 | 1): number | null {
    const projectedName = this.dataset?.features?.[index]?.name ?? null;
    // 未选择或缺少特征元数据时退回投影坐标，避免整张图空白
    if (!dimName || !projectedName || dimName === projectedName) {
      return index === 0 ? point.x : point.y;
    }
    const value = point.values?.[dimName];
    return typeof value === 'number' && isFinite(value) ? value : null;
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
    // 轴名在 setOption 之前取好，避免 tooltip 闭包读到后续变更
    const xAxisLabel = this.isPcaView ? 'PC1' : this.getAxisDisplayName(this.selectedXName, 'Feature X');
    const yAxisLabel = this.isPcaView ? 'PC2' : this.getAxisDisplayName(this.selectedYName, 'Feature Y');

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
            return `${params.seriesName}<br/>${xAxisLabel}: ${params.value[0]}<br/>${yAxisLabel}: ${params.value[1]}`;
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
        name: xAxisLabel,
        nameLocation: 'middle',
        nameGap: 34,
        nameTextStyle: { color: '#1e293b', fontWeight: 600, fontSize: 11 },
        axisLine: { lineStyle: { color: '#94A3B8' } },
        axisLabel: { color: '#334155', fontSize: 10, fontWeight: 500 },
        splitLine: { lineStyle: { color: 'rgba(15,23,42,0.06)' } }
      },
      yAxis: {
        type: 'value',
        name: yAxisLabel,
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

    // 索引必须是原始行号：聚类 assignments 按下标对齐
    this.dataset?.dataPoints.forEach((point, index) => {
      const x = this.getPointAxisValue(point, this.selectedXName, 0);
      const y = this.getPointAxisValue(point, this.selectedYName, 1);
      if (x === null || y === null) {
        return; // 该行缺少所选维度，不绘制
      }
      const key = this.resolvePointLabel(point.label, point.clusterId, index);
      if (!grouped[key]) {
        grouped[key] = [];
      }
      grouped[key].push([x, y]);
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
    const assignments = this.activeAssignments;
    if (index === undefined || !assignments || assignments.length !== this.dataset?.dataPoints?.length) {
      return undefined;
    }
    return assignments[index];
  }

  /**
   * 聚类结果按下标与数据行对齐，只有投影视图（不跳点）才成立；
   * 换了坐标轴就退回按 label / clusterId 着色。
   */
  private get activeAssignments(): number[] | undefined {
    return this.isProjectionView ? this.trainingState?.modelData?.assignments : undefined;
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

    // 叠加线都是在训练的投影维度里算出来的，换轴后画上去必然错位；
    // 连合成的动画兜底线/兜底聚类中心也一并隐藏（它们同样基于旧的 x/y 范围）。
    if (this.isPcaView || !this.isProjectionView) {
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
