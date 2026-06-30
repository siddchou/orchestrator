import { Component, Input, OnInit, OnDestroy, ViewChild, ElementRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { Subscription } from 'rxjs';
import { LogStreamService } from '../../../core/services/log-stream.service';

@Component({
  selector: 'app-log-viewer',
  imports: [CommonModule, MatSlideToggleModule, MatButtonModule, MatIconModule, FormsModule],
  templateUrl: './log-viewer.component.html',
  styleUrl: './log-viewer.component.scss',
})
export class LogViewerComponent implements OnInit, OnDestroy {
  @Input() runId!: number;
  @ViewChild('logContainer') logContainer!: ElementRef<HTMLDivElement>;

  private logStream = inject(LogStreamService);

  logLines: string[] = [];
  autoScroll = true;
  running = true;
  private sub?: Subscription;

  ngOnInit(): void {
    this.sub = this.logStream.streamLog(this.runId).subscribe({
      next: (line: string) => {
        this.logLines.push(line);
        if (this.autoScroll) this.scrollToBottom();
      },
      complete: () => {
        this.running = false;
        this.logLines.push('');
        this.logLines.push('─── Run complete ───');
      },
      error: () => {
        this.running = false;
        this.logLines.push('');
        this.logLines.push('─── Stream disconnected ───');
      },
    });
  }

  clearLog(): void {
    this.logLines = [];
  }

  scrollToBottom(): void {
    setTimeout(() => {
      const el = this.logContainer?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    }, 0);
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }
}
