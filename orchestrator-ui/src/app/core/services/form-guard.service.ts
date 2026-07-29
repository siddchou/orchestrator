import { Injectable, inject } from '@angular/core';
import { FormGroup } from '@angular/forms';
import { BehaviorSubject } from 'rxjs';

/**
 * Central registry for dirty forms. Components with editable forms call
 * markDirty() / markClean () so the TeamSwitcher can warn before reload.
 */
@Injectable({ providedIn: 'root' })
export class FormGuardService {
  private readonly dirty$ = new BehaviorSubject<boolean>(false);

  /** Call from a form's valueChanges/statusChanges when it becomes dirty */
  markDirty(): void {
    this.dirty$.next(true);
  }

  /** Call when the form is saved/reset */
  markClean(): void {
    this.dirty$.next(false);
  }

  /** Synchronous check — used by TeamSwitcher before reload */
  get hasUnsavedChanges(): boolean {
    return this.dirty$.value;
  }
}
