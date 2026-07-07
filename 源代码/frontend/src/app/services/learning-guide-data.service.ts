import { Injectable } from '@angular/core';
import { Algorithm, Dataset, ParameterDef, TrainingState } from '../models/algorithm.model';
import {
  CaseGuidanceState,
  EvaluationPanelState,
  ExperimentSimulationState,
  GuidedExperimentCase,
  LoadedExperimentConfig,
  ModelExplanationState,
  SimulationContext,
  TrainingPoint
} from '../models/learning-guide.model';

@Injectable({
  providedIn: 'root'
})
export class LearningGuideDataService {
  private readonly mockAlgorithms: Algorithm[] = [
    {
      id: 1,
      name: 'linear_regression',
      displayName: '线性回归',
      description: '使用可解释的特征权重拟合连续目标值。',
      category: 'supervised',
      type: 'regression',
      parameters: this.numberParams([
        ['learningRate', '学习率', 0.03, 0.001, 0.2, 0.001],
        ['iterations', '迭代次数', 100, 20, 200, 10]
      ])
    },
    {
      id: 2,
      name: 'logistic_regression',
      displayName: '逻辑回归',
      description: '具有线性决策边界和特征权重的二分类算法。',
      category: 'supervised',
      type: 'classification',
      parameters: this.numberParams([
        ['learningRate', '学习率', 0.08, 0.001, 0.2, 0.001],
        ['iterations', '迭代次数', 120, 20, 240, 10]
      ])
    },
    {
      id: 3,
      name: 'decision_tree',
      displayName: '决策树',
      description: '贪心特征分裂，可直接可视化为可解释的树结构。',
      category: 'supervised',
      type: 'classification',
      parameters: [
        ...this.numberParams([
          ['maxDepth', '最大深度', 4, 1, 8, 1],
          ['minSamplesSplit', '最小分裂样本数', 2, 2, 10, 1]
        ]),
        {
          name: 'criterion',
          displayName: '分裂准则',
          type: 'select',
          default: 'gini',
          options: ['gini', 'entropy']
        }
      ]
    },
    {
      id: 4,
      name: 'kmeans',
      displayName: 'K-Means聚类',
      description: '围绕聚类中心对样本进行聚类，比较不同K值的效果。',
      category: 'unsupervised',
      type: 'clustering',
      parameters: this.numberParams([
        ['k', '聚类数量', 3, 2, 6, 1],
        ['maxIterations', '最大迭代次数', 80, 20, 200, 10]
      ])
    },
    {
      id: 5,
      name: 'svm',
      displayName: '支持向量机',
      description: '基于边界的分类器，可调节正则化参数。',
      category: 'supervised',
      type: 'classification',
      parameters: [
        {
          name: 'kernel',
          displayName: '核函数',
          type: 'select',
          default: 'rbf',
          options: ['linear', 'rbf']
        },
        ...this.numberParams([
          ['c', '正则化参数C', 1, 0.1, 4, 0.1],
          ['iterations', '迭代次数', 80, 20, 200, 10]
        ])
      ]
    }
  ];

  private readonly mockDatasets: Dataset[] = [
    {
      id: 1,
      name: 'iris',
      description: '经典花卉分类数据集，投影到两个花瓣特征维度。',
      category: 'supervised',
      featureCount: 4,
      sampleCount: 150,
      features: [
        { name: 'petal_length', displayName: '花瓣长度', type: 'number' },
        { name: 'petal_width', displayName: '花瓣宽度', type: 'number' }
      ],
      dataPoints: [
        { x: 1.4, y: 0.2, label: 'setosa' },
        { x: 1.5, y: 0.3, label: 'setosa' },
        { x: 4.5, y: 1.5, label: 'versicolor' },
        { x: 4.7, y: 1.4, label: 'versicolor' },
        { x: 5.8, y: 2.2, label: 'virginica' },
        { x: 6.1, y: 2.4, label: 'virginica' }
      ]
    },
    {
      id: 2,
      name: 'boston_housing',
      description: '小型回归数据集，用于解释拟合线和特征权重。',
      category: 'supervised',
      featureCount: 13,
      sampleCount: 80,
      features: [
        { name: 'rm', displayName: '平均房间数', type: 'number' },
        { name: 'medv', displayName: '中位数价值', type: 'number' }
      ],
      dataPoints: [
        { x: 5.5, y: 18 },
        { x: 6.0, y: 21 },
        { x: 6.4, y: 24 },
        { x: 6.8, y: 28 },
        { x: 7.1, y: 31 }
      ]
    },
    {
      id: 3,
      name: 'make_classification',
      description: '合成2D分类数据，用于边界和指标演示。',
      category: 'supervised',
      featureCount: 2,
      sampleCount: 100,
      features: [
        { name: 'x', displayName: '特征X', type: 'number' },
        { name: 'y', displayName: '特征Y', type: 'number' }
      ],
      dataPoints: [
        { x: 1.2, y: 2.1, label: '0' },
        { x: 1.5, y: 1.8, label: '0' },
        { x: 3.1, y: 4.0, label: '1' },
        { x: 3.5, y: 4.2, label: '1' },
        { x: 2.3, y: 2.9, label: '0' },
        { x: 4.0, y: 4.8, label: '1' }
      ]
    },
    {
      id: 4,
      name: 'make_blobs',
      description: '聚类2D数据块，用于K-Means和轮廓系数演示。',
      category: 'unsupervised',
      featureCount: 2,
      sampleCount: 120,
      features: [
        { name: 'x', displayName: '特征X', type: 'number' },
        { name: 'y', displayName: '特征Y', type: 'number' }
      ],
      dataPoints: [
        { x: 1.0, y: 1.9, clusterId: 0 },
        { x: 1.4, y: 2.3, clusterId: 0 },
        { x: 5.2, y: 6.2, clusterId: 1 },
        { x: 5.7, y: 6.5, clusterId: 1 },
        { x: 8.1, y: 8.8, clusterId: 2 },
        { x: 8.4, y: 9.0, clusterId: 2 }
      ]
    }
  ];

  private readonly guidedCases: GuidedExperimentCase[] = [
    {
      id: 'case-kmeans-iris',
      title: '使用K-Means聚类Iris数据',
      summary: '观察K值如何影响iris花瓣特征的聚类紧凑性。',
      category: 'unsupervised',
      algorithmName: 'kmeans',
      datasetName: 'make_blobs',
      parameterPreset: { k: 3, maxIterations: 80 },
      objectives: [
        '加载K=3的聚类预设。',
        '运行训练并监控轮廓系数的改进。',
        '使用解释面板比较聚类中心分离说明。'
      ],
      validationText: '轮廓系数应稳定在0.70以上，聚类中心摘要应提到三个紧凑的组。'
    },
    {
      id: 'case-logistic-boundary',
      title: '分类合成数据点',
      summary: '训练逻辑回归并检查特征权重如何驱动决策边界。',
      category: 'supervised',
      algorithmName: 'logistic_regression',
      datasetName: 'make_classification',
      parameterPreset: { learningRate: 0.08, iterations: 120 },
      objectives: [
        '加载分类预设。',
        '观察训练过程中准确率、精确率、召回率和F1值的提升。',
        '验证权重图表中特征Y是最强的正向信号。'
      ],
      validationText: '准确率应超过90%，解释面板应显示特征Y具有最强的正向权重。'
    },
    {
      id: 'case-tree-depth',
      title: '解释决策树',
      summary: '检查最大深度如何改变iris数据上树的结构和纯度。',
      category: 'supervised',
      algorithmName: 'decision_tree',
      datasetName: 'iris',
      parameterPreset: { maxDepth: 4, minSamplesSplit: 2, criterion: 'gini' },
      objectives: [
        '加载决策树预设。',
        '运行训练直到树解释出现。',
        '使用结构视图识别根节点分裂和叶节点纯度。'
      ],
      validationText: '树应渲染至少五个节点，根节点在花瓣长度上分裂，并有清晰的类别纯度说明。'
    },
    {
      id: 'case-linear-weights',
      title: '解释线性回归器',
      summary: '跟踪损失下降并解释住房数据的特征权重大小。',
      category: 'supervised',
      algorithmName: 'linear_regression',
      datasetName: 'boston_housing',
      parameterPreset: { learningRate: 0.03, iterations: 100 },
      objectives: [
        '加载回归预设。',
        '观察损失曲线在迭代过程中的下降。',
        '确认平均房间数仍是最强的正向系数。'
      ],
      validationText: '损失应稳定下降，解释面板应显示平均房间数作为首要正向驱动因素。'
    },
    {
      id: 'case-svm-margin',
      title: '比较边界分类器',
      summary: '在合成数据集上使用SVM并检查RBF核函数下的指标。',
      category: 'supervised',
      algorithmName: 'svm',
      datasetName: 'make_classification',
      parameterPreset: { kernel: 'rbf', c: 1.2, iterations: 80 },
      objectives: [
        '加载SVM预设。',
        '运行训练以将其指标面板与逻辑回归进行比较。',
        '阅读关于边界稳定性和支持向量的摘要解释。'
      ],
      validationText: 'F1值应保持较高，解释面板应描述稳定的分离边界。'
    }
  ];

  getMockAlgorithms(): Algorithm[] {
    return this.mockAlgorithms;
  }

  getMockDatasets(): Dataset[] {
    return this.mockDatasets;
  }

  getGuidedCases(): GuidedExperimentCase[] {
    return this.guidedCases;
  }

  loadCase(caseId: string): LoadedExperimentConfig {
    const guidedCase = this.guidedCases.find((item) => item.id === caseId);
    if (!guidedCase) {
      throw new Error(`Unknown case: ${caseId}`);
    }

    const algorithm = this.mockAlgorithms.find((item) => item.name === guidedCase.algorithmName);
    const dataset = this.mockDatasets.find((item) => item.name === guidedCase.datasetName);
    if (!algorithm || !dataset) {
      throw new Error(`Missing preset for case ${caseId}`);
    }

    return {
      algorithm,
      dataset,
      parameters: { ...guidedCase.parameterPreset }
    };
  }

  buildSimulation(context: SimulationContext): ExperimentSimulationState {
    const algorithm = context.algorithm;
    const dataset = context.dataset;
    if (!algorithm || !dataset) {
      return {
        evaluation: {
          title: '评估指标',
          subtitle: '选择算法和数据集以开始。',
          cards: [],
          chart: [],
          scoreLabel: '得分',
          lossLabel: '损失'
        },
        explanation: {
          mode: 'summary',
          title: '模型解释',
          description: '选择算法以解锁特征或结构解释。',
          notes: ['决策树渲染节点图。', '线性模型渲染权重条。', 'K-Means显示聚类中心行为说明。']
        },
        statusText: '等待实验设置。'
      };
    }

    const totalSteps = context.trainingState.totalSteps || this.getTotalStepsFromParams(context.parameters, algorithm);
    const progress = totalSteps
      ? context.trainingState.currentStep / totalSteps
      : 0;
    const chart = this.buildTrainingCurve(progress, algorithm.type, totalSteps);

    if (algorithm.name === 'decision_tree') {
      return {
        evaluation: this.buildClassificationEvaluation(chart, progress, '当前深度下的树准确率'),
        explanation: this.buildDecisionTreeExplanation(progress, context.parameters),
        statusText: progress >= 1 ? '树结构已验证，可以查看解释。' : '树正在逐层分裂生长。'
      };
    }

    if (algorithm.name === 'linear_regression' || algorithm.name === 'logistic_regression') {
      return {
        evaluation: algorithm.name === 'linear_regression'
          ? this.buildRegressionEvaluation(chart, progress)
          : this.buildClassificationEvaluation(chart, progress, '边界质量和类别置信度'),
        explanation: this.buildWeightExplanation(algorithm.name, progress),
        statusText: progress >= 1 ? '权重已稳定，可以进行解释。' : '权重正在每次优化步骤中更新。'
      };
    }

    if (algorithm.name === 'kmeans') {
      return {
        evaluation: this.buildClusteringEvaluation(chart, progress, Number(context.parameters['k'] || 3)),
        explanation: this.buildKMeansExplanation(progress, Number(context.parameters['k'] || 3)),
        statusText: progress >= 1 ? '聚类中心已稳定。' : '聚类中心仍在向密集区域移动。'
      };
    }

    return {
      evaluation: this.buildClassificationEvaluation(chart, progress, '当前参数下的边界质量'),
      explanation: {
        mode: 'summary',
        title: '支持向量摘要',
        description: 'SVM强调边界稳定性而非直接可解释的特征权重。',
        notes: [
          '当前边界以高置信度分离两个合成类别。',
          '正则化参数C控制边界的软硬程度。',
          '使用指标面板将SVM与逻辑回归进行比较。'
        ]
      },
      statusText: progress >= 1 ? '边界已稳定，指标可供比较。' : '支持向量仍在识别中。'
    };
  }

  buildGuidanceState(guidedCaseId: string | null, trainingState: TrainingState): CaseGuidanceState {
    if (!guidedCaseId) {
      return {
        caseId: null,
        phase: 'idle',
        activeStep: 0,
        headline: '加载预设案例以获取引导式工作流程。'
      };
    }

    const progress = trainingState.totalSteps ? trainingState.currentStep / trainingState.totalSteps : 0;
    if (trainingState.status === 'idle' && trainingState.currentStep === 0) {
      return {
        caseId: guidedCaseId,
        phase: 'configured',
        activeStep: 1,
        headline: '预设已加载。请检查推荐的算法、数据集和参数。'
      };
    }

    if (trainingState.status === 'training' || trainingState.status === 'paused') {
      return {
        caseId: guidedCaseId,
        phase: 'running',
        activeStep: progress > 0.6 ? 3 : 2,
        headline: '训练正在进行。请同时查看指标面板和解释卡片。'
      };
    }

    return {
      caseId: guidedCaseId,
      phase: 'verified',
      activeStep: 4,
      headline: '验证通过。请查看解释视图并与案例目标进行比较。'
    };
  }

  private buildDecisionTreeExplanation(progress: number, parameters: Record<string, any>): ModelExplanationState {
    const maxDepth = Number(parameters['maxDepth'] || 4);
    return {
      mode: 'tree',
      title: '决策树结构',
      description: `深度${maxDepth}允许树将iris特征空间分裂成越来越纯的叶节点。`,
      notes: [
        '根节点分裂强调花瓣长度，因为它能早期分离setosa。',
        '左分支快速变得纯净，而右分支仍需区分versicolor和virginica。',
        progress >= 1 ? '树生长完成；每个节点内有悬停等效的详细信息。' : '随着训练进行，后续叶节点细化类别纯度。'
      ],
      treeNodes: [
        { id: 'root', x: 170, y: 26, label: '花瓣长度 < 2.4', detail: '150样本 · gini 0.66', tone: 'cyan' },
        { id: 'left', x: 62, y: 132, label: 'setosa', detail: '50样本 · 纯度 1.00', tone: 'green' },
        { id: 'right', x: 278, y: 132, label: '花瓣宽度 < 1.8', detail: '100样本 · gini 0.49', tone: 'purple' },
        { id: 'mid-left', x: 220, y: 238, label: 'versicolor', detail: '47样本 · 纯度 0.91', tone: 'green' },
        { id: 'mid-right', x: 336, y: 238, label: 'virginica', detail: '53样本 · 纯度 0.94', tone: 'cyan' }
      ],
      treeEdges: [
        { from: 'root', to: 'left' },
        { from: 'root', to: 'right' },
        { from: 'right', to: 'mid-left' },
        { from: 'right', to: 'mid-right' }
      ]
    };
  }

  private buildWeightExplanation(modelName: string, progress: number): ModelExplanationState {
    const scale = 0.4 + progress * 0.6;
    const weights = modelName === 'linear_regression'
      ? [
          { feature: '平均房间数', value: 0.92 * scale },
          { feature: 'LSTAT', value: -0.61 * scale },
          { feature: 'PTRATIO', value: -0.35 * scale },
          { feature: 'NOX', value: -0.21 * scale }
        ]
      : [
          { feature: '特征Y', value: 0.88 * scale },
          { feature: '特征X', value: 0.42 * scale },
          { feature: '偏置', value: -0.28 * scale }
        ];

    return {
      mode: 'weights',
      title: modelName === 'linear_regression' ? '线性特征权重' : '逻辑特征权重',
      description: '正权重将预测向上或向类别1推进，负权重则向相反方向拉动。',
      notes: [
        '权重条按绝对贡献排序，主导因素排在顶部。',
        modelName === 'linear_regression'
          ? '平均房间数对更高的房价预测有正向贡献。'
          : '特征Y主导向正类别的向上移动。'
      ],
      weights
    };
  }

  private buildKMeansExplanation(progress: number, k: number): ModelExplanationState {
    return {
      mode: 'summary',
      title: '聚类中心行为摘要',
      description: `K-Means当前在特征空间中追踪${k}个聚类中心作为聚类锚点。`,
      notes: [
        '聚类中心开始时分散，随着迭代增加变得更加紧凑。',
        '轮廓系数衡量当前聚类的分离程度。',
        progress >= 1 ? '聚类中心已稳定，可比较K值选择并报告最佳分组。' : '观察聚类中心稳定过程中聚类分数的提升。'
      ]
    };
  }

  private buildClassificationEvaluation(chart: TrainingPoint[], progress: number, subtitle: string): EvaluationPanelState {
    return {
      title: '评估指标',
      subtitle,
      scoreLabel: '准确率',
      lossLabel: '损失',
      cards: [
        { label: '准确率', value: 0.74 + progress * 0.19, suffix: '%', tone: 'cyan', icon: 'track_changes' },
        { label: '精确率', value: 0.7 + progress * 0.2, suffix: '%', tone: 'purple', icon: 'check_circle' },
        { label: '召回率', value: 0.68 + progress * 0.22, suffix: '%', tone: 'green', icon: 'radar' },
        { label: 'F1值', value: 0.69 + progress * 0.21, suffix: '%', tone: 'amber', icon: 'insights' }
      ],
      chart
    };
  }

  private buildRegressionEvaluation(chart: TrainingPoint[], progress: number): EvaluationPanelState {
    return {
      title: '回归质量',
      subtitle: '跟踪优化过程中的拟合质量和误差收缩。',
      scoreLabel: 'R²',
      lossLabel: 'MSE',
      cards: [
        { label: 'R²', value: 0.45 + progress * 0.42, suffix: '%', display: 'ratioPercent', tone: 'cyan', icon: 'show_chart', trend: '+0.03' },
        { label: 'MAE', value: 12.6 - progress * 4.2, display: 'number', tone: 'purple', icon: 'straighten', trend: '-1.2' },
        { label: 'MSE', value: 28.4 - progress * 11.8, display: 'number', tone: 'green', icon: 'timeline', trend: '-3.1' },
        { label: '损失下降', value: 18 + progress * 52, suffix: '%', display: 'percent', tone: 'amber', icon: 'trending_down', trend: '+2%' }
      ],
      chart
    };
  }

  private buildClusteringEvaluation(chart: TrainingPoint[], progress: number, k: number): EvaluationPanelState {
    return {
      title: '聚类指标',
      subtitle: `检查K = ${k}的聚类紧凑性。`,
      scoreLabel: '轮廓系数',
      lossLabel: '惯性',
      cards: [
        { label: '轮廓系数', value: 0.42 + progress * 0.35, suffix: '%', tone: 'cyan', icon: 'bubble_chart' },
        { label: '惯性下降', value: 18 + progress * 58, suffix: '%', tone: 'purple', icon: 'compress' },
        { label: '聚类数量', value: k, tone: 'green', icon: 'filter_3' },
        { label: '稳定性', value: 0.5 + progress * 0.34, suffix: '%', tone: 'amber', icon: 'auto_awesome' }
      ],
      chart
    };
  }

  private buildTrainingCurve(progress: number, type: string, totalSteps: number = 8): TrainingPoint[] {
    const totalPoints = Math.min(totalSteps, 20); // Cap at 20 for chart readability
    return Array.from({ length: totalPoints }, (_, index) => {
      const ratio = (index + 1) / totalPoints;
      const effective = Math.min(1, ratio * (0.4 + progress * 0.8));
      return {
        step: Math.round((index + 1) * (totalSteps / totalPoints)),
        loss: Number((1.12 - effective * (type === 'clustering' ? 0.55 : 0.82)).toFixed(3)),
        score: Number((0.28 + effective * (type === 'regression' ? 0.52 : 0.60)).toFixed(3))
      };
    });
  }

  private numberParams(rows: Array<[string, string, number, number, number, number]>): ParameterDef[] {
    return rows.map(([name, displayName, defaultValue, min, max, step]) => ({
      name,
      displayName,
      type: 'number',
      default: defaultValue,
      min,
      max,
      step
    }));
  }

  private getTotalStepsFromParams(parameters: Record<string, any>, algorithm: Algorithm): number {
    const iterations = parameters['iterations'] || parameters['maxIterations'];
    if (iterations) {
      return Number(iterations);
    }
    if (algorithm.name === 'decision_tree') {
      const maxDepth = parameters['maxDepth'] || 4;
      return Number(maxDepth) * 2;
    }
    return 8;
  }
}
