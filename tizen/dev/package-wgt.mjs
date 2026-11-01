// dist/ → 서명된 KomgaTV.wgt (Tizen CLI).
// 실행: npm run build && node dev/package-wgt.mjs <보안 프로필 이름>
// TIZEN_STUDIO 환경 변수 = Tizen Studio 설치 폴더 (기본 C:\tizen-studio).
// 에뮬레이터는 Tizen 작성자 인증서 프로필로, 실기 TV는 삼성 인증서(기기 DUID 포함) 프로필로 서명해야 한다.
import { execFileSync } from 'node:child_process';
import { existsSync, readdirSync, readFileSync, renameSync, rmSync } from 'node:fs';
import { join, resolve } from 'node:path';

const profile = process.argv[2];
if (!profile) {
  console.error('usage: node dev/package-wgt.mjs <security-profile>');
  process.exit(2);
}
const studio = process.env.TIZEN_STUDIO ?? 'C:\\tizen-studio';
const tizen = join(studio, 'tools', 'ide', 'bin', process.platform === 'win32' ? 'tizen.bat' : 'tizen');
if (!existsSync(tizen)) {
  console.error(`Tizen CLI not found: ${tizen} (set TIZEN_STUDIO)`);
  process.exit(2);
}
const dist = resolve('dist');
for (const name of readdirSync(dist)) {
  // 이전 서명·패키지 찌꺼기 제거
  if (name.endsWith('.wgt') || /signature\d*\.xml$/.test(name) || name === '.manifest.tmp') rmSync(join(dist, name));
}
// NoDefaultCurrentDirectoryInExePath가 켜져 있으면 Tizen 도구의 배치 파일이 현재 폴더의 스크립트를 못 찾는다
const env = { ...process.env };
delete env.NoDefaultCurrentDirectoryInExePath;
const run = (args) =>
  process.platform === 'win32'
    ? execFileSync('cmd.exe', ['/c', tizen, ...args], { stdio: 'inherit', env })
    : execFileSync(tizen, args, { stdio: 'inherit', env });
run(['package', '-t', 'wgt', '-s', profile, '--', dist]);
const wgt = readdirSync(dist).find((name) => name.endsWith('.wgt'));
if (!wgt) {
  console.error('no .wgt produced');
  process.exit(1);
}
// 파일 이름에 버전과 빌드 번호 (Android APK와 같게: KomgaTV-v0.2.2-b4.wgt)
const { version, build } = JSON.parse(readFileSync('package.json', 'utf8'));
const named = `KomgaTV-v${version}-b${build}.wgt`;
renameSync(join(dist, wgt), join(dist, named));
console.log(join(dist, named));
