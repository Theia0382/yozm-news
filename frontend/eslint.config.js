import js from "@eslint/js";
import prettier from "eslint-config-prettier";
import reactHooks from "eslint-plugin-react-hooks";
import reactRefresh from "eslint-plugin-react-refresh";
import { defineConfig, globalIgnores } from "eslint/config";
import globals from "globals";
import tseslint from "typescript-eslint";

export default defineConfig([
  // 생성 파일(src/shared/api/schema.d.ts)은 flat config 가 .gitignore 를 읽지 않으므로 여기서도 뺀다
  globalIgnores(["dist", "coverage", "src/shared/api/schema.d.ts"]),
  {
    files: ["**/*.{ts,tsx}"],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      globals: globals.browser,
    },
  },
  {
    // shadcn 생성 컴포넌트는 컴포넌트와 변형 함수(buttonVariants 등)를 함께 내보낸다
    files: ["src/shared/ui/**/*.tsx"],
    rules: {
      "react-refresh/only-export-components": "off",
    },
  },
  prettier,
]);
