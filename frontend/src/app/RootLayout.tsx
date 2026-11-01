import { Outlet } from "react-router";

export function RootLayout() {
  return (
    <div className="min-h-svh bg-background text-foreground">
      <main className="py-12">
        <Outlet />
      </main>
    </div>
  );
}
