// ESLint flat config. compat 플러그인이 package.json의 browserslist("chrome 69")로
// 2020년형 TV(Chromium M69)에 없는 Web API 사용을 잡는다 (docs/PLAN.md Z1).
import js from '@eslint/js';
import compat from 'eslint-plugin-compat';
import globals from 'globals';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  { ignores: ['dist/', 'node_modules/', '.buildResult/'] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  compat.configs['flat/recommended'],
  {
    files: ['src/**/*.{ts,tsx}'],
    languageOptions: { globals: { ...globals.browser } },
  },
  {
    files: ['test/**/*.ts', 'dev/**/*.mjs', 'build.mjs', 'vitest.config.ts'],
    languageOptions: { globals: { ...globals.node } },
    // 테스트·빌드 스크립트는 PC(Node)에서만 돈다
    rules: { 'compat/compat': 'off' },
  },
);
