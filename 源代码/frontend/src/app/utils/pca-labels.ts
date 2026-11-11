/**
 * 根据 PCA 载荷矩阵为每个主成分自动生成「含义标签」。
 * 规则：
 *  - 主导特征：|载荷| 从大到小取前 3 个，且 |载荷| >= 0.35；
 *  - 若主导特征名有公共前缀（按 _ / 空格 / - 切词），用它做概念名，如 petal_length/petal_width -> "petal"；
 *  - 所有主导特征同号 -> 「正向综合轴」；正负混合 -> 「对比轴」；
 *  - 标签 = 「概念名 + 正向综合轴 / 对比轴」，轴向描述列出主导特征及其载荷。
 */
import { TrainingModelData } from '../models/algorithm.model';

export interface PcaDominantFeature {
  name: string;
  loading: number;
}

export interface PcaComponentLabel {
  /** 主成分序号（从 0 开始） */
  index: number;
  /** 方差解释率（0~1），数据缺失时为 null */
  ratio: number | null;
  /** 短标签，例如 "petal 正向综合轴"，用于坐标轴与徽章 */
  shortLabel: string;
  /** 完整解读，例如 "主要由 petal_length(+0.86)、petal_width(+0.36) 构成，方向一致，可看作 petal 相关维度的正向综合轴" */
  description: string;
  /** 主导特征列表（最多 3 个） */
  dominant: PcaDominantFeature[];
}

const DOMINANT_THRESHOLD = 0.35;
const DOMINANT_MAX = 3;

/** 取两个特征名的公共词前缀（如 petal_length & petal_width -> "petal"） */
function commonWordPrefix(names: string[]): string {
  if (names.length < 2) {
    return '';
  }
  const splitWords = (name: string) =>
    name.toLowerCase().split(/[_\s\-]+/).filter((w) => w.length > 0);
  const first = splitWords(names[0]);
  if (!first.length) {
    return '';
  }
  let prefixWords = first;
  for (let i = 1; i < names.length; i++) {
    const words = splitWords(names[i]);
    const keep: string[] = [];
    for (let j = 0; j < Math.min(prefixWords.length, words.length); j++) {
      if (prefixWords[j] === words[j]) {
        keep.push(words[j]);
      } else {
        break;
      }
    }
    prefixWords = keep;
    if (!prefixWords.length) {
      return '';
    }
  }
  return prefixWords.join(' ');
}

/** 从 modelData 推导每个主成分的含义标签 */
export function describePcaComponents(modelData?: TrainingModelData): PcaComponentLabel[] {
  const components = modelData?.pcaComponents;
  const names = modelData?.featureNames ?? [];
  if (!components?.length || !names.length) {
    return [];
  }
  const ratios = modelData?.pcaExplainedVariance ?? [];

  return components.map((vector, index) => {
    const ranked: PcaDominantFeature[] = vector
      .map((loading, featureIndex) => ({ name: names[featureIndex] ?? `dim${featureIndex + 1}`, loading: loading ?? 0 }))
      .sort((a, b) => Math.abs(b.loading) - Math.abs(a.loading))
      .filter((f) => Math.abs(f.loading) >= DOMINANT_THRESHOLD)
      .slice(0, DOMINANT_MAX);

    // 特征特别多时阈值可能把前 3 全过滤掉，兜底保留第一名
    if (!ranked.length && vector.length) {
      const top = vector
        .map((loading, featureIndex) => ({ name: names[featureIndex] ?? `dim${featureIndex + 1}`, loading: loading ?? 0 }))
        .sort((a, b) => Math.abs(b.loading) - Math.abs(a.loading))[0];
      ranked.push(top);
    }

    const dominantNames = ranked.map((f) => f.name);
    const concept = commonWordPrefix(dominantNames) || dominantNames[0] || `PC${index + 1}`;
    const sameSign = ranked.length > 0 && ranked.every((f) => Math.sign(f.loading) === Math.sign(ranked[0].loading));
    const kindLabel = ranked.length <= 1 ? '主导轴' : sameSign ? '正向综合轴' : '对比轴';

    const featureList = ranked
      .map((f) => `${f.name}(${f.loading >= 0 ? '+' : ''}${f.loading.toFixed(2)})`)
      .join('、');
    const relation = ranked.length <= 1
      ? '贡献最大'
      : sameSign
        ? '方向一致'
        : '方向相反';
    const description = ranked.length
      ? `主要由 ${featureList} 构成，${relation}，可看作「${concept}」相关的${kindLabel}`
      : '各原始维度贡献都较小，未找到主导特征';

    return {
      index,
      ratio: ratios.length > index ? ratios[index] : null,
      shortLabel: `${concept}·${kindLabel}`,
      description,
      dominant: ranked
    };
  });
}
