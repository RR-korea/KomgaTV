// esbuild가 CSS를 app.css로 묶으므로 TS에는 부작용 import만 알려 준다.
declare module '*.css';

/** package.json version (build.mjs의 define) */
declare const APP_VERSION: string;
