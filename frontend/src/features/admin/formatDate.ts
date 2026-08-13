export function formatDate(timestamp: string): string {
  return timestamp.slice(0, 10);
}

export function formatDateTime(timestamp: string): string {
  return `${timestamp.slice(0, 10)} ${timestamp.slice(11, 19)} UTC`;
}
