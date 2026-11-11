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
import { EvaluationPanelState, MetricCard } from '../../models/learning-guide.model';
import { PcaSummary } from '../../models/pca.model';

interface MetricViewModel {
  label: string;
  value: string;
  color: string;
  delta: string;
  positive: boolean;
}

@Component({
  selector: 'app-evaluation-panel',
  templateUrl: './evaluation-panel.component.html',
  styleUrls: ['./evaluation-panel.component.scss']
})
export class EvaluationPanelComponent implements AfterViewInit, OnChanges, OnDestroy {
  @Input() evaluation: EvaluationPanelState | null = null;
  /** PCA 结果：非空时本面板改为展示主成分分析，不再渲染指标卡与训练轨迹 */
  @Input() pca: PcaSummary | null = null;

  @ViewChild('chartHost')
  set chartHostRef(host: ElementRef<HTMLDivElement> | undefined) {
    if (!host && this.chart) {
      this.chart.dispose();
      this.chart = null;
    }
    this.chartHost = host;
    this.scheduleRenderChart();
  }

  private chartHost?: ElementRef<HTMLDivElement>;
  private chart: echarts.ECharts | null = null;
  private renderHandle: ReturnType<typeof setTimeout> | null = null;

  ngAfterViewInit(): void {
    this.scheduleRenderChart();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['pca']) {
      if (this.pca) {
        // 切到 PCA 模式：训练轨迹不存在了，销毁 ECharts 实例
        this.chart?.dispose();
        this.chart = null;
      }
      this.scheduleRenderChart();
    }
    if (changes['evaluation']) {
      this.scheduleRenderChart();
    }
  }

  ngOnDestroy(): void {
    if (this.renderHandle) {
      clearTimeout(this.renderHandle);
    }
    this.chart?.dispose();
  }

  @HostListener('window:resize')
  onResize(): void {
    this.chart?.resize();
  }

  get metricCards(): MetricViewModel[] {
    const cards = this.evaluation?.cards || [];
    return cards.map((card) => ({
      label: this.normalizeLabel(card.label),
      value: this.formatCardValue(card),
      color: this.getToneColor(card.tone),
      delta: this.getDelta(card),
      positive: this.isPositive(card)
    }));
  }

  normalizeLabel(label: string): string {
    const trimmed = label.trim();
    return /^R.{0,2}$/.test(trimmed) ? 'R²' : trimmed;
  }

  private formatCardValue(card: MetricCard): string {
    // 训练还没开始：卡片只显示标签，不出现任何数字
    if (card.value === undefined || card.value === null || Number.isNaN(card.value)) {
      return '';
    }
    const mode = card.display || (card.suffix === '%' ? (card.value <= 1.2 ? 'ratioPercent' : 'percent') : 'number');
    if (mode === 'ratioPercent') {
      return `${(card.value * 100).toFixed(1)}${card.suffix || ''}`;
    }
    if (mode === 'percent') {
      return `${card.value.toFixed(0)}${card.suffix || ''}`;
    }
    return `${card.value.toFixed(card.value >= 10 ? 1 : 2)}${card.suffix || ''}`;
  }

  private getToneColor(tone: string): string {
    const tones: Record<string, string> = {
      cyan: '#378ADD',
      purple: '#A78BFA',
      green: '#10B981',
      amber: '#F59E0B'
    };
    return tones[tone] || '#378ADD';
  }

  /** 只显示真实的上一步差值；没有上一步（只有基线点）时为空 */
  private getDelta(card: MetricCard): string {
    if (card.delta === undefined || card.delta === null || Number.isNaN(card.delta)) {
      return '';
    }
    const sign = card.delta > 0 ? '+' : '';
    const decimals = Math.abs(card.delta) < 0.01 ? 4 : 2;
    return `${sign}${card.delta.toFixed(decimals)} 较上一步`;
  }

  private isPositive(card: MetricCard): boolean {
    if (card.delta === undefined || card.delta === null) {
      return true;
    }
    return card.lowerIsBetter ? card.delta <= 0 : card.delta >= 0;
  }

  private scheduleRenderChart(): void {
    if (this.renderHandle) {
      clearTimeout(this.renderHandle);
    }
    this.renderHandle = setTimeout(() => {
      this.renderHandle = null;
      this.renderChart();
    });
  }

  private renderChart(): void {
    // PCA 模式只展示主成分分析，没有训练轨迹
    if (this.pca) {
      return;
    }
    if (!this.chartHost?.nativeElement || !this.evaluation) {
      return;
    }

    if (!this.chart) {
      this.chart = echarts.init(this.chartHost.nativeElement, undefined, { renderer: 'canvas' });
    }

    const chartData = this.evaluation.chart;
    const lossLabel = this.normalizeLabel(this.evaluation.lossLabel);
    const scoreLabel = this.normalizeLabel(this.evaluation.scoreLabel);
    const totalSteps = this.evaluation.totalSteps;

    this.chart.setOption({
      animation: true,
      animationDuration: 500,
      animationDurationUpdate: 220,
      animationEasing: 'cubicOut',
      animationEasingUpdate: 'cubicOut',
      grid: {
        left: 48,
        right: 42,
        top: 18,
        bottom: 34
      },
      tooltip: {
        trigger: 'axis',
        backgroundColor: '#111827',
        borderColor: 'rgba(255,255,255,0.08)',
        textStyle: {
          color: '#E2E8F0'
        },
        formatter: (params: any) => {
          const items = Array.isArray(params) ? params : [params];
          const step = items[0]?.value?.[0] ?? '';
          const rows = items.map((item: any) => `${item.marker}${item.seriesName}: ${item.value?.[1]}`);
          return `步骤 ${step}<br/>${rows.join('<br/>')}`;
        }
      },
      xAxis: {
        type: 'value',
        min: 0,
        max: totalSteps && totalSteps > 0 ? totalSteps : undefined,
        minInterval: 1,
        axisLine: { lineStyle: { color: 'rgba(255,255,255,0.12)' } },
        axisLabel: { color: '#94A3B8', fontSize: 10, formatter: (value: number) => `${Math.round(value)}` },
        splitLine: { show: false }
      },
      yAxis: [
        {
          type: 'value',
          name: lossLabel,
          nameTextStyle: { color: '#64748B', fontSize: 10, padding: [0, 0, 8, 0] },
          axisLabel: { color: '#94A3B8', fontSize: 10 },
          splitLine: { lineStyle: { color: 'rgba(255,255,255,0.06)' } }
        },
        {
          type: 'value',
          name: scoreLabel,
          nameTextStyle: { color: '#64748B', fontSize: 10, padding: [0, 0, 8, 0] },
          axisLabel: {
            color: '#94A3B8',
            fontSize: 10,
            formatter: (value: number) => `${Math.round(value * 100)}%`
          },
          splitLine: { show: false }
        }
      ],
      series: [
        {
          name: lossLabel,
          type: 'line',
          yAxisIndex: 0,
          smooth: 0.4,
          symbol: 'circle',
          symbolSize: 5,
          showSymbol: false,
          lineStyle: { width: 1.5, color: '#38BDF8' },
          itemStyle: {
            color: '#38BDF8',
            borderColor: 'rgba(56,189,248,0.35)',
            borderWidth: 4
          },
          emphasis: {
            scale: false,
            itemStyle: {
              color: '#fff',
              borderColor: '#38BDF8',
              borderWidth: 2,
              shadowBlur: 10,
              shadowColor: 'rgba(56,189,248,0.55)'
            }
          },
          animationDelay: (index: number) => index * 42,
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(56,189,248,0.13)' },
              { offset: 0.7, color: 'rgba(56,189,248,0.03)' },
              { offset: 1, color: 'rgba(56,189,248,0)' }
            ])
          },
          data: chartData.map((point) => [point.step, point.loss])
        },
        {
          name: scoreLabel,
          type: 'line',
          yAxisIndex: 1,
          smooth: 0.4,
          symbol: 'circle',
          symbolSize: 5,
          showSymbol: false,
          lineStyle: { width: 1.5, color: '#C084FC' },
          itemStyle: {
            color: '#C084FC',
            borderColor: 'rgba(192,132,252,0.35)',
            borderWidth: 4
          },
          emphasis: {
            scale: false,
            itemStyle: {
              color: '#fff',
              borderColor: '#C084FC',
              borderWidth: 2,
              shadowBlur: 10,
              shadowColor: 'rgba(192,132,252,0.55)'
            }
          },
          animationDelay: (index: number) => index * 42 + 80,
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(192,132,252,0.11)' },
              { offset: 0.7, color: 'rgba(192,132,252,0.03)' },
              { offset: 1, color: 'rgba(192,132,252,0)' }
            ])
          },
          data: chartData.map((point) => [point.step, point.score])
        }
      ]
    });
    this.chart.resize();
  }
}
