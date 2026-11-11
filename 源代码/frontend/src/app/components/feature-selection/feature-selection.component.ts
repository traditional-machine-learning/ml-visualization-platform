import { Component, OnInit } from '@angular/core';
import { ApiService } from '../../services/api.service';
import { Dataset, FeatureInfo, TrainingSimulationResponse, Algorithm } from '../../models/algorithm.model';

@Component({
  selector: 'app-feature-selection',
  templateUrl: './feature-selection.component.html',
  styleUrls: ['./feature-selection.component.scss']
})
export class FeatureSelectionComponent implements OnInit {
  datasets: Dataset[] = [];
  algorithms: Algorithm[] = [];
  selectedDataset?: Dataset;
  selectedFeatures: string[] = [];
  algorithmId?: number;
  result?: TrainingSimulationResponse;

  constructor(private api: ApiService) { }

  ngOnInit(): void {
    this.api.getDatasets().subscribe(r => { if (r.success) this.datasets = r.data; });
    this.api.getAlgorithms().subscribe(r => { if (r.success) this.algorithms = r.data; });
  }

  onDatasetChange(idStr: any) {
    const id = Number(idStr);
    const dataset = this.datasets.find(d => d.id === id);
    this.selectedDataset = dataset;
    // 列表接口只返回投影后的两维，先用它兜底
    this.selectedFeatures = this.selectableNames(dataset?.features);

    if (!dataset) {
      return;
    }
    // 详情接口带全维度元数据，特征选择才能在全部维度里做（标签列由后端自动保留）
    this.api.getDatasetById(id).subscribe({
      next: (r) => {
        if (!r.success || this.selectedDataset?.id !== id) {
          return;
        }
        this.selectedDataset = r.data;
        this.selectedFeatures = this.selectableNames(r.data.dimensions ?? r.data.features);
      },
      error: () => { /* 保持列表接口的精简版 */ }
    });
  }

  private selectableNames(features?: FeatureInfo[]): string[] {
    return (features ?? [])
      .filter(f => f.type !== 'label')
      .map(f => f.name);
  }

  toggleFeature(name: string) {
    const idx = this.selectedFeatures.indexOf(name);
    if (idx >= 0) {
      this.selectedFeatures.splice(idx, 1);
    } else {
      this.selectedFeatures.push(name);
    }
  }

  isFeatureSelected(name: string): boolean {
    return this.selectedFeatures.indexOf(name) >= 0;
  }

  apply() {
    if (!this.algorithmId || !this.selectedDataset) return;
    this.api.selectFeatures(this.algorithmId, this.selectedDataset.id, this.selectedFeatures).subscribe(r => {
      if (r.success) this.result = r.data;
    });
  }
}
