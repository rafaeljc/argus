export function AuthGate() {
  return (
    <div
      role="status"
      aria-live="polite"
      aria-label="Loading"
      className="flex min-h-screen items-center justify-center"
    >
      <span className="sr-only">Loading…</span>
    </div>
  );
}
