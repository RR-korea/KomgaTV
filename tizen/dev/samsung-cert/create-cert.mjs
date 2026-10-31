// 삼성 TV(VD) 작성자·배포자 인증서 발급 → PKCS#12 (Tizen Studio cert-add-on과 같은 API·CA).
// 사용: TOKEN_FILE=<login.mjs 결과> VD_CERTS=<vd_tizen_dev_*.cer/crt 폴더> \
//       node dev/samsung-cert/create-cert.mjs <출력 폴더(저장소 밖)> <작성자 이름> <이메일> <DUID,...>
// VD CA 파일은 Tizen Studio cert-add-on 플러그인 jar(org.tizen.common.cert_*.jar)의 res/ca/ 안에 있다.
// DUID: sdb -s <기기> shell 0 getduid. 기기를 더하면 이 스크립트를 다시 돌린다 — 작성자 인증서는 그대로 두고 배포자만 새로 받는다.
// p12 비밀번호는 출력 폴더의 password.txt (없으면 만든다). 출력 폴더는 절대 커밋하지 않는다.
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { randomBytes } from 'node:crypto';
import forge from 'node-forge';

const [outDir, authorName, email, duidArg] = process.argv.slice(2);
const duids = (duidArg ?? '').split(',').filter(Boolean);
if (!outDir || !authorName || !email || !duids.length) {
  console.error('usage: node create-cert.mjs <outDir> <authorName> <email> <DUID,...>');
  process.exit(2);
}
mkdirSync(outDir, { recursive: true });
const pwFile = join(outDir, 'password.txt');
if (!existsSync(pwFile)) writeFileSync(pwFile, randomBytes(15).toString('base64url'));
const password = readFileSync(pwFile, 'utf8').trim();
const token = JSON.parse(readFileSync(process.env.TOKEN_FILE ?? 'token.json', 'utf8'));
const vd = (name) => readFileSync(join(process.env.VD_CERTS ?? 'vdcerts', name), 'utf8');

function csr(subject, altNames) {
  const keys = forge.pki.rsa.generateKeyPair(2048);
  const request = forge.pki.createCertificationRequest();
  request.publicKey = keys.publicKey;
  request.setSubject(subject);
  if (altNames) {
    request.setAttributes([{ name: 'extensionRequest', extensions: [{ name: 'subjectAltName', altNames }] }]);
  }
  request.sign(keys.privateKey, forge.md.sha512.create());
  return { csr: forge.pki.certificationRequestToPem(request), key: keys.privateKey };
}

async function post(url, fields, csrPem, filename) {
  const form = new FormData();
  for (const [k, v] of Object.entries(fields)) form.append(k, v);
  form.append('csr', new Blob([csrPem], { type: 'application/octet-stream' }), filename);
  const response = await fetch(url, { method: 'POST', body: form });
  const text = await response.text();
  if (!response.ok) throw new Error(`${url} → ${response.status}: ${text.slice(0, 500)}`);
  return text;
}

function toP12(key, certPems, file) {
  const certs = certPems.map((pem) => forge.pki.certificateFromPem(pem));
  const asn1 = forge.pkcs12.toPkcs12Asn1(key, certs, password, {
    algorithm: '3des',
    generateLocalKeyId: true,
    friendlyName: 'UserCertificate',
  });
  writeFileSync(file, Buffer.from(forge.asn1.toDer(asn1).getBytes(), 'binary'));
  const leaf = certs[0];
  console.log(`${file}: ${leaf.subject.getField('CN')?.value} <- ${leaf.issuer.getField('CN')?.value}, until ${leaf.validity.notAfter.toISOString().slice(0, 10)}`);
}

const common = { access_token: token.access_token, user_id: token.userId, platform: 'VD' };

// 작성자: 이미 있으면 그대로 둔다 (같은 작성자 키로 서명해야 업데이트 설치가 된다)
const authorP12 = join(outDir, 'author.p12');
if (!existsSync(authorP12)) {
  const author = csr([{ name: 'commonName', value: authorName }]);
  const authorPem = await post('https://svdca.samsungqbe.com/apis/v3/authors', common, author.csr, 'author.csr');
  writeFileSync(join(outDir, 'author.crt'), authorPem);
  toP12(author.key, [authorPem, vd('vd_tizen_dev_author_ca.cer')], authorP12);
} else {
  console.log(`${authorP12}: kept`);
}

// 배포자: 기기 DUID 목록을 담는다 (기기를 더하면 다시 발급)
const distributor = csr(
  [
    { name: 'commonName', value: 'TizenSDK' },
    { name: 'emailAddress', value: email },
  ],
  [{ type: 6, value: 'URN:tizen:packageid=' }, ...duids.map((d) => ({ type: 6, value: `URN:tizen:deviceid=${d}` }))],
);
const distributorPem = await post(
  'https://svdca.samsungqbe.com/apis/v3/distributors',
  { ...common, privilege_level: 'Public', developer_type: 'Individual' },
  distributor.csr,
  'distributor.csr',
);
writeFileSync(join(outDir, 'distributor.crt'), distributorPem);
toP12(distributor.key, [distributorPem, vd('vd_tizen_dev_public2.crt')], join(outDir, 'distributor.p12'));
writeFileSync(join(outDir, 'duids.txt'), duids.join('\n'));
console.log(`DUIDs: ${duids.join(', ')}`);
