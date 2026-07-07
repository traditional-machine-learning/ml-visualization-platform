import { Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges } from '@angular/core';
import { Dataset } from '../../models/algorithm.model';
import { ApiService } from '../../services/api.service';
import { LearningGuideDataService } from '../../services/learning-guide-data.service';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-dataset-selector',
  templateUrl: './dataset-selector.component.html',
  styleUrls: ['./dataset-selector.component.scss']
})
export class DatasetSelectorComponent implements OnInit, OnChanges {
  @Input() useMockData = environment.demoMode;
  @Input() selectedDataset: Dataset | null = null;
  @Output() datasetSelect = new EventEmitter<Dataset>();

  datasets: Dataset[] = [];
  isLoading: boolean = false;

  constructor(private apiService: ApiService, private learningGuideData: LearningGuideDataService) {}

  ngOnInit() {
    this.loadDatasets();
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['useMockData'] && !changes['useMockData'].firstChange) {
      this.loadDatasets();
    }
  }

  loadDatasets() {
    if (this.useMockData) {
      this.datasets = this.learningGuideData.getMockDatasets();
      this.isLoading = false;
      return;
    }

    this.isLoading = true;
    this.apiService.getDatasets().subscribe({
      next: (response) => {
        if (response.success) {
          this.datasets = response.data;
        }
        this.isLoading = false;
      },
      error: (error) => {
        console.error('Failed to load datasets:', error);
        if (!this.datasets.length) {
          this.datasets = this.learningGuideData.getMockDatasets();
        }
        this.isLoading = false;
      }
    });
  }

  onSelect(dataset: Dataset) {
    this.selectedDataset = dataset;
    this.datasetSelect.emit(dataset);
  }

  isSelected(dataset: Dataset): boolean {
    return this.selectedDataset?.id === dataset.id;
  }

  onFileUpload(fileInput: any) {
    const file = fileInput.files[0];
    if (file) {
      console.log('CSV file selected:', file.name);
    }
  }
}
