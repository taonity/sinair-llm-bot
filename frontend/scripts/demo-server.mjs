import { spawn, spawnSync } from 'node:child_process'
import { cpSync, mkdtempSync, readdirSync, rmSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('../../', import.meta.url))
const frontend = fileURLToPath(new URL('../', import.meta.url))
const backend = process.argv[2] === 'backend'
const jars = backend
  ? readdirSync(`${root}/backend/target`).filter((name) => /^backend-.*\.jar$/.test(name))
  : []
if (backend && jars.length !== 1) throw new Error('Run npm run demo:build before capturing screenshots')

const runtime = backend ? mkdtempSync(path.join(root, 'backend/target/demo-runtime-')) : null
if (runtime) {
  // WireMock cannot read classpath mappings through Spring Boot's nested JAR URLs.
  const extraction = spawnSync('jar', ['xf', `${root}/backend/target/${jars[0]}`], { cwd: runtime, stdio: 'inherit' })
  if (extraction.error || extraction.status !== 0) {
    rmSync(runtime, { recursive: true, force: true })
    throw extraction.error ?? new Error('Could not extract the demo backend')
  }
} else {
  cpSync(path.join(frontend, '.next/static'), path.join(frontend, '.next/standalone/.next/static'), { recursive: true })
  cpSync(path.join(frontend, 'public'), path.join(frontend, '.next/standalone/public'), { recursive: true })
}

const child = spawn(
  backend ? 'java' : process.execPath,
  backend
    ? ['-Duser.timezone=UTC', '-cp', `${runtime}/BOOT-INF/classes${path.delimiter}${runtime}/BOOT-INF/lib/*`, 'org.taonity.sinairllmbot.SinairLlmBotApplicationKt', '--spring.profiles.active=demo']
    : ['.next/standalone/server.js'],
  {
    cwd: backend ? root : frontend,
    stdio: 'inherit',
    env: {
      ...process.env,
      LOCAL_BACKEND_URL: 'http://127.0.0.1:8080',
      PUBLIC_BACKEND_URL: 'http://127.0.0.1:8080',
      CSRF_COOKIE_NAME: 'XSRF-TOKEN-DEMO',
      APP_ENVIRONMENT: 'demo',
      HOSTNAME: '127.0.0.1',
      PORT: '3000',
    },
  },
)

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => child.kill(signal))
}
function cleanRuntime() {
  if (runtime) rmSync(runtime, { recursive: true, force: true })
}

child.on('error', (error) => {
  cleanRuntime()
  throw error
})
child.on('exit', (code) => {
  cleanRuntime()
  process.exit(code ?? 1)
})
