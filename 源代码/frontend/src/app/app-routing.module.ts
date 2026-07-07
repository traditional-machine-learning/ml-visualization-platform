import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ExperimentComponent } from './experiment/experiment.component';

const routes: Routes = [
  { path: '', redirectTo: '/experiment', pathMatch: 'full' },
  { path: 'experiment', component: ExperimentComponent },
  { path: '**', redirectTo: '/experiment' }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
