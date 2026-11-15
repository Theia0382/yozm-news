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
        children: [
          { index: true, element: <HomePage /> },
          {
            // 알림 페이지는 필요할 때 내려받는다 — 머리줄의 알림 링크만 첫 화면 묶음에 든다
            path: "notices",
            lazy: async () => ({
              Component: (await import("@/features/notices/NoticesPage"))
                .NoticesPage,
            }),
          },
          {
            path: "settings",
            lazy: async () => ({
              Component: (await import("@/features/account/SettingsPage"))
                .SettingsPage,
            }),
          },
          {
            // LLM 키 화면 — 키가 없는 사용자는 설정 · 알림 밖의 어느 주소로 와도 여기로 간다
            path: "keys",
            lazy: async () => ({
              Component: (await import("@/features/keys/KeysPage")).KeysPage,
            }),
          },
          {
            // 첫 로그인의 비밀번호 변경 — 변경이 남은 계정은 어느 주소로 와도 여기로 간다
            path: "password",
            lazy: async () => ({
              Component: (await import("@/features/account/PasswordPage"))
                .PasswordPage,
            }),
          },
        ],
      },
      {
        // 로그인 화면은 로그인하지 않은 방문자만 받으므로 필요할 때 내려받는다
        path: "login",
        lazy: async () => ({
          Component: (await import("@/features/session/LoginPage")).LoginPage,
        }),
      },
      {
        // 가입 · 초대 가입 · 복구 재설정은 로그인하지 않은 방문자의 화면이라 필요할 때 내려받는다
        path: "sign-up",
        lazy: async () => ({
          Component: (await import("@/features/account/SignUpPage")).SignUpPage,
        }),
      },
      {
        path: "invite",
        lazy: async () => ({
          Component: (await import("@/features/account/InvitePage")).InvitePage,
        }),
      },
      {
        path: "recover",
        lazy: async () => ({
          Component: (await import("@/features/account/RecoverPage"))
            .RecoverPage,
        }),
      },
      {
        // 고지 페이지는 로그인 없이 본다 — 가입 · 키 · 설정 화면이 링크한다
        path: "privacy",
        lazy: async () => ({
          Component: (await import("@/features/privacy/PrivacyPage"))
            .PrivacyPage,
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
            path: "accounts",
            lazy: async () => ({
              Component: (await import("@/features/admin/AccountsPage"))
                .AccountsPage,
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
