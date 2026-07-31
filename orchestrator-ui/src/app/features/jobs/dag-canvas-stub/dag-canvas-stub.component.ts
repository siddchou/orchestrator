import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-dag-canvas-stub',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatIconModule],
  templateUrl: './dag-canvas-stub.component.html',
  styleUrl: './dag-canvas-stub.component.scss',
})
export class DagCanvasStubComponent {}
