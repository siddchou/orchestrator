import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { downloadFile } from './file-utils';

describe('downloadFile', () => {
  let createObjectURLSpy: any;
  let revokeObjectURLSpy: any;
  let mockA: HTMLAnchorElement;

  beforeEach(() => {
    mockA = document.createElement('a');
    mockA.click = vi.fn();

    createObjectURLSpy = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:test-url');
    revokeObjectURLSpy = vi.spyOn(URL, 'revokeObjectURL');
    vi.spyOn(document, 'createElement').mockImplementation((tag) => {
      if (tag === 'a') return mockA;
      return document.createElement(tag);
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('should download a string as a text/plain Blob', () => {
    downloadFile('hello world', 'test.txt');

    expect(createObjectURLSpy).toHaveBeenCalledOnce();
    const blobArg = createObjectURLSpy.mock.calls[0][0] as Blob;
    expect(blobArg.type).toBe('text/plain');
    expect(mockA.download).toBe('test.txt');
    expect(mockA.click).toHaveBeenCalled();
    expect(revokeObjectURLSpy).toHaveBeenCalledWith('blob:test-url');
  });

  it('should download a Blob with its original type', () => {
    const blob = new Blob(['json data'], { type: 'application/json' });
    downloadFile(blob, 'data.json');

    expect(createObjectURLSpy).toHaveBeenCalledOnce();
    expect(createObjectURLSpy.mock.calls[0][0]).toBe(blob);
    expect(mockA.download).toBe('data.json');
  });
});
