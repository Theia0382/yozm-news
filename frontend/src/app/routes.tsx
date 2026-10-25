import type { RouteObject } from "react-router";
import { RootLayout } from "@/app/RootLayout";
import { HomePage } from "@/features/home/HomePage";

export const routes: RouteObject[] = [
  {
    path: "/",
    element: <RootLayout />,
    children: [{ index: true, element: <HomePage /> }],
  },
];
