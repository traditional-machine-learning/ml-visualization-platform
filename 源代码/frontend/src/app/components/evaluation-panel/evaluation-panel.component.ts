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
    return cards.map((card, index) => {
      const positive = this.isPositive(card);
      return {
        label: this.normalizeLabel(card.label),
        value: this.formatCardValue(card),
        color: this.getToneColor(card.tone),
        delta: this.getDelta(card, index),
        positive
      };
    });
  }

  normalizeLabel(label: string): string {
    const trimmed = label.trim();
    return /^R.{0,2}$/.test(trimmed) ? 'R²' : trimmed;
  }

  private formatCardValue(card: MetricCard): string {
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

  private getDelta(card: MetricCard, index: number): string {
    if (card.trend) {
      return `${card.trend} vs 上次运行`;
    }
    const defaults = ['+0.03 vs 上次运行', '+2.1% vs 上次运行', '-1.2 vs 上次运行', '+2% vs 上次运行'];
    return defaults[index % defaults.length];
  }

  private isPositive(card: MetricCard): boolean {
    const label = this.normalizeLabel(card.label).toLowerCase();
    return !(label.includes('mae') || label.includes('mse'));
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
    if (!this.chartHost?.nativeElement || !this.evaluation?.chart?.length) {
      return;
    }

    if (!this.chart) {
      this.chart = echarts.init(this.chartHost.nativeElement, undefined, { renderer: 'canvas' });
    }

    const chartData = this.evaluation.chart;
    const labels = chartData.map((point) => `S${point.step}`);
    const lossLabel = this.normalizeLabel(this.evaluation.lossLabel);
    const scoreLabel = this.normalizeLabel(this.evaluation.scoreLabel);

    this.chart.setOption({
      animation: true,
      animationDuration: 800,
      animationDurationUpdate: 650,
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
        }
      },
      xAxis: {
        type: 'category',
        data: labels,
        boundaryGap: false,
        axisLine: { lineStyle: { color: 'rgba(255,255,255,0.12)' } },
        axisLabel: { color: '#94A3B8', fontSize: 10 },
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
          data: chartData.map((point) => point.loss)
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
          data: chartData.map((point) => point.score)
        }
      ]
    });
    this.chart.resize();
  }
}
