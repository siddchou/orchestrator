import { Component, Input, OnInit, OnDestroy, ViewChild, ElementRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { Subscription } from 'rxjs';
import { LogStreamService } from '@app/core/services/log-stream.service';

interface LogLine {
  text: string;
  level: 'error' | 'warn' | 'info' | 'debug' | 'default';
}

const LEVEL_RE = /^\[?\s*(ERROR|FATAL|WARN|WARNING|INFO|DEBUG)\]?\s*[:\-]?\s*/i;

function detectLevel(line: string): LogLine['level'] {
  const m = LEVEL_RE.exec(line);
  if (!m) return 'default';
  const raw = m[1].toUpperCase();
  if (raw === 'ERROR' || raw === 'FATAL') return 'error';
  if (raw === 'WARN' || raw === 'WARNING') return 'warn';
  if (raw === 'INFO') return 'info';
  if (raw === 'DEBUG') return 'debug';
  return 'default';
}

function escapeHtml(str: string): string {
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

@Component({
  selector: 'app-log-viewer',
  imports: [
    CommonModule, FormsModule, MatSlideToggleModule, MatButtonModule,
    MatIconModule, MatInputModule, MatFormFieldModule,
  ],
  standalone: true,
  templateUrl: './log-viewer.component.html',
  styleUrl: './log-viewer.component.scss',
})
export class LogViewerComponent implements OnInit, OnDestroy {
  @Input() runId!: number;
  @ViewChild('logContainer') logContainer!: ElementRef<HTMLDivElement>;

  private logStream = inject(LogStreamService);

  logLines: LogLine[] = [];
  filteredLines: LogLine[] = [];
  autoScroll = true;
  lineWrap = false;
  running = true;
  searchQuery = '';
  private sub?: Subscription;

  ngOnInit(): void {
    this.sub = this.logStream.streamLog(this.runId).subscribe({
      next: (line: string) => {
        const logLine: LogLine = { text: line, level: detectLevel(line) };
        this.logLines.push(logLine);
        this.applyFilter();
        if (this.autoScroll) this.scrollToBottom();
      },
      complete: () => {
        this.running = false;
        this.logLines.push({ text: '', level: 'default' });
        this.logLines.push({ text: '─── Run complete ───', level: 'default' });
        this.applyFilter();
      },
      error: () => {
        this.running = false;
        this.logLines.push({ text: '', level: 'default' });
        this.logLines.push({ text: '─── Stream disconnected ───', level: 'default' });
        this.applyFilter();
      },
    });
  }

  onSearchChange(): void {
    this.applyFilter();
  }

  private applyFilter(): void {
    const q = this.searchQuery.trim().toLowerCase();
    if (!q) {
      this.filteredLines = this.logLines;
    } else {
      this.filteredLines = this.logLines.filter(l => l.text.toLowerCase().includes(q));
    }
  }

  highlightMatch(line: LogLine): string {
    if (!this.searchQuery.trim()) return escapeHtml(line.text);
    const escaped = escapeHtml(line.text);
    const query = escapeHtml(this.searchQuery.trim());
    const idx = escaped.toLowerCase().indexOf(query.toLowerCase());
    if (idx === -1) return escaped;
    const before = escaped.slice(0, idx);
    const match = escaped.slice(idx, idx + query.length);
    const after = escaped.slice(idx + query.length);
    return `${before}<mark class="log-highlight">${match}</mark>${after}`;
  }

  clearLog(): void {
    this.logLines = [];
    this.filteredLines = [];
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
