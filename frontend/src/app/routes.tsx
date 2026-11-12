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
            path: "collection",
            lazy: async () => ({
              Component: (await import("@/features/admin/CollectionPage"))
                .CollectionPage,
            }),
          },
          {
            path: "evaluation",
            lazy: async () => ({
              Component: (await import("@/features/admin/EvaluationPage"))
                .EvaluationPage,
            }),
          },
          {
            path: "evaluations",
            lazy: async () => ({
              Component: (await import("@/features/admin/EvaluationListPage"))
                .EvaluationListPage,
            }),
          },
          {
            path: "evaluations/:id",
            lazy: async () => ({
              Component: (await import("@/features/admin/EvaluationDetailPage"))
                .EvaluationDetailPage,
            }),
          },
          {
            path: "samples",
            lazy: async () => ({
              Component: (await import("@/features/admin/SamplePage"))
                .SamplePage,
            }),
          },
          {
            path: "thresholds",
            lazy: async () => ({
              Component: (await import("@/features/admin/ThresholdsPage"))
                .ThresholdsPage,
            }),
          },
          {
            path: "events",
            lazy: async () => ({
              Component: (await import("@/features/admin/EventsPage"))
                .EventsPage,
            }),
          },
          {
            path: "events/:id",
            lazy: async () => ({
              Component: (await import("@/features/admin/EventDetailPage"))
                .EventDetailPage,
            }),
          },
          {
            path: "big-storylines",
            lazy: async () => ({
              Component: (await import("@/features/admin/BigStorylinesPage"))
                .BigStorylinesPage,
            }),
          },
          {
            path: "event-judgements",
            lazy: async () => ({
              Component: (await import("@/features/admin/EventJudgementsPage"))
                .EventJudgementsPage,
            }),
          },
          {
            path: "event-publications",
            lazy: async () => ({
              Component: (
                await import("@/features/admin/EventPublicationsPage")
              ).EventPublicationsPage,
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
