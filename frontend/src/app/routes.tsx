import type { RouteObject } from "react-router";
import { RootLayout } from "@/app/RootLayout";
import { HomePage } from "@/features/home/HomePage";

export const routes: RouteObject[] = [
  {
    path: "/",
    element: <RootLayout />,
    children: [
      { index: true, element: <HomePage /> },
      {
        // 운영자 화면은 필요할 때만 내려받는다
        path: "admin",
        lazy: async () => ({
          Component: (await import("@/features/admin/AdminLayout")).AdminLayout,
        }),
        children: [
          {
            index: true,
            lazy: async () => ({
              Component: (await import("@/features/admin/SourcesPage"))
                .SourcesPage,
            }),
          },
          {
            path: "sources/new",
            lazy: async () => ({
              Component: (await import("@/features/admin/ProfileEditorPage"))
                .ProfileEditorPage,
            }),
          },
          {
            path: "sources/:id/profile",
            lazy: async () => ({
              Component: (await import("@/features/admin/ProfileEditorPage"))
                .ProfileEditorPage,
            }),
          },
        ],
      },
    ],
  },
];
