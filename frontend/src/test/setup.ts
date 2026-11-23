import "@testing-library/jest-dom/vitest";
import { cleanup, configure } from "@testing-library/react";
import { afterEach } from "vitest";

// 부하에서는 파일의 첫 화면이 기본 기다림(1초) 안에 다 그려지지 않을 수 있어 늘린다 — Vitest 의 시험 시간 한도(기본 5초)보다 작아야
// 시험 시간 초과가 아니라 기다림의 실패 메시지가 남는다
configure({ asyncUtilTimeout: 3000 });

afterEach(() => {
  cleanup();
});
