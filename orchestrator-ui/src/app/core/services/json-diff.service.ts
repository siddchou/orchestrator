import { Injectable } from '@angular/core';
import { diff } from 'jsondiffpatch';
import { format } from 'jsondiffpatch/formatters/html';

/** Service for comparing two JSON objects and producing an HTML diff */
@Injectable({ providedIn: 'root' })
export class JsonDiffService {
  /**
   * Compare two objects and return an HTML string showing the differences.
   * Returns undefined if the objects are identical (no diff).
   */
  compare(left: unknown, right: unknown): string | undefined {
    const delta = diff(left, right);
    if (!delta) return undefined;
    return format(delta, left as Record<string, unknown>) ?? undefined;
  }
}
