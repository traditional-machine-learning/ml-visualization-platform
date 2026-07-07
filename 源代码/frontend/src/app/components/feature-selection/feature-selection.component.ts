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
    this.selectedDataset = this.datasets.find(d => d.id === id);
    // Reset selected features when dataset changes
    this.selectedFeatures = [];
    if (this.selectedDataset && this.selectedDataset.features) {
      // Default select all features
      this.selectedFeatures = this.selectedDataset.features.map(f => f.name);
    }
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
