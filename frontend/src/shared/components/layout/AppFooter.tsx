export function AppFooter() {
  const year = new Date().getFullYear();
  return (
    <footer className="border-t border-slate-200 bg-white">
      <div className="mx-auto flex w-full max-w-6xl flex-col items-center justify-between gap-2 px-4 py-4 text-sm text-slate-500 sm:flex-row sm:px-6 lg:px-8">
        <span>© {year} Argus</span>
      </div>
    </footer>
  );
}
