import type { RouteObject } from "react-router";
import { RootLayout } from "@/app/RootLayout";
import { HomePage } from "@/features/home/HomePage";
import { RequireSignIn } from "@/features/session/RequireSignIn";

export const routes: RouteObject[] = [
  {
    path: "/",
    element: <RootLayout />,
    children: [
      {
        // 로그인한 사용자만 보는 화면 — 로그인하지 않았으면 로그인 화면으로 보낸다
        element: <RequireSignIn />,
        children: [{ index: true, element: <HomePage /> }],
      },
      {
        // 로그인 화면은 로그인하지 않은 방문자만 받으므로 필요할 때 내려받는다
        path: "login",
        lazy: async () => ({
          Component: (await import("@/features/session/LoginPage")).LoginPage,
        }),
      },
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
            path: "storylines",
            lazy: async () => ({
              Component: (await import("@/features/admin/StorylinesPage"))
                .StorylinesPage,
            }),
          },
          {
            path: "storylines/:id",
            lazy: async () => ({
              Component: (await import("@/features/admin/StorylineDetailPage"))
                .StorylineDetailPage,
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
