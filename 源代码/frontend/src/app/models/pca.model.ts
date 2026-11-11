import { PcaComponentLabel } from '../utils/pca-labels';

/** PCA 结果摘要：方差解释率、累计解释率、载荷矩阵与主成分含义标签 */
export interface PcaSummary {
  k: number;
  explained: number[];
  cumulative: number[];
  loadings: { name: string; values: number[] }[];
  /** 每个主成分的含义标签（主导特征 + 轴类型） */
  labels: PcaComponentLabel[];
}
