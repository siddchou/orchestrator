/**
 * Trigger a browser download of content as a file.
 * Works with Blob data or plain strings (wrapped in a Blob).
 */
export function downloadFile(content: Blob | string, filename: string): void {
  const blob = content instanceof Blob ? content : new Blob([content], { type: 'text/plain' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}
