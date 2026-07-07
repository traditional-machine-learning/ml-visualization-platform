import { Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges } from '@angular/core';
import { Algorithm } from '../../models/algorithm.model';
import { ApiService } from '../../services/api.service';
import { LearningGuideDataService } from '../../services/learning-guide-data.service';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-algorithm-selector',
  templateUrl: './algorithm-selector.component.html',
  styleUrls: ['./algorithm-selector.component.scss']
})
export class AlgorithmSelectorComponent implements OnInit, OnChanges {
  @Input() useMockData = environment.demoMode;
  @Input() selectedAlgorithm: Algorithm | null = null;
  @Output() algorithmSelect = new EventEmitter<Algorithm>();

  algorithms: Algorithm[] = [];
  isLoading: boolean = false;

  constructor(private apiService: ApiService, private learningGuideData: LearningGuideDataService) {}

  ngOnInit() {
    this.loadAlgorithms();
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['useMockData'] && !changes['useMockData'].firstChange) {
      this.loadAlgorithms();
    }
  }

  loadAlgorithms() {
    if (this.useMockData) {
      this.algorithms = this.learningGuideData.getMockAlgorithms();
      this.isLoading = false;
      return;
    }

    this.isLoading = true;
    this.apiService.getAlgorithms().subscribe({
      next: (response) => {
        if (response.success) {
          this.algorithms = response.data;
          this.ensureAdvancedAlgorithmsPresent();
        }
        this.isLoading = false;
      },
      error: (error) => {
        console.error('Failed to load algorithms:', error);
        if (!this.algorithms.length) {
          this.algorithms = this.learningGuideData.getMockAlgorithms();
        }
        this.isLoading = false;
      }
    });
  }

  private ensureAdvancedAlgorithmsPresent() {
    const names = this.algorithms.map(a => a.name);
    const extras = [];
    if (!names.includes('pca')) {
      extras.push({ id: -101, name: 'pca', displayName: 'PCA', description: '降维：主成分分析', category: 'unsupervised', type: 'pca', parameters: [] } as Algorithm);
    }
    if (!names.includes('random_forest') && !names.includes('rf') ) {
      extras.push({ id: -102, name: 'random_forest', displayName: '随机森林', description: '集成学习：随机森林（后端需支持）', category: 'supervised', type: 'ensemble', parameters: [] } as Algorithm);
    }
    if (extras.length) {
      this.algorithms = [...this.algorithms, ...extras];
    }
  }

  onSelect(algorithm: Algorithm) {
    this.selectedAlgorithm = algorithm;
    this.algorithmSelect.emit(algorithm);
  }

  isSelected(algorithm: Algorithm): boolean {
    return this.selectedAlgorithm?.id === algorithm.id;
  }
}
