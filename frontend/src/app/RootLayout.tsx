import { Outlet } from "react-router";

export function RootLayout() {
  return (
    <div className="min-h-svh bg-background text-foreground">
      <main className="mx-auto max-w-3xl px-4 py-12">
        <Outlet />
      </main>
    </div>
  );
}
