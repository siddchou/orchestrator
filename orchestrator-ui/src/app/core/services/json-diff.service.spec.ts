import { TestBed } from '@angular/core/testing';
import { JsonDiffService } from './json-diff.service';

describe('JsonDiffService', () => {
  let service: JsonDiffService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(JsonDiffService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('compare', () => {
    it('returns undefined for identical objects', () => {
      const obj = { name: 'test', count: 42 };
      expect(service.compare(obj, obj)).toBeUndefined();
    });

    it('returns HTML diff when values change', () => {
      const left = { name: 'old', count: 10 };
      const right = { name: 'new', count: 20 };
      const html = service.compare(left, right);

      expect(html).toBeDefined();
      expect(typeof html).toBe('string');
      expect(html).toContain('jsondiffpatch-delta');
    });

    it('returns HTML diff when a property is added', () => {
      const left = { name: 'test' };
      const right = { name: 'test', extra: 'added' };
      const html = service.compare(left, right);

      expect(html).toBeDefined();
      expect(html).toContain('jsondiffpatch-added');
    });

    it('returns HTML diff when a property is removed', () => {
      const left = { name: 'test', extra: 'removed' };
      const right = { name: 'test' };
      const html = service.compare(left, right);

      expect(html).toBeDefined();
      expect(html).toContain('jsondiffpatch-deleted');
    });

    it('handles nested object changes', () => {
      const left = { config: { timeout: 30, retries: 2 } };
      const right = { config: { timeout: 60, retries: 5 } };
      const html = service.compare(left, right);

      expect(html).toBeDefined();
      expect(html).toContain('jsondiffpatch-modified');
    });

    it('handles array changes', () => {
      const left = { steps: ['build', 'test'] };
      const right = { steps: ['build', 'test', 'deploy'] };
      const html = service.compare(left, right);

      expect(html).toBeDefined();
    });

    it('handles null and undefined inputs', () => {
      expect(service.compare(null, null)).toBeUndefined();
      expect(service.compare(undefined, {})).toBeDefined();
    });
  });
});
