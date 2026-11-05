import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('../../', import.meta.url))
const frontend = fileURLToPath(new URL('../', import.meta.url))

for (const [command, args, cwd] of [
  ['mvn', ['-B', '-pl', 'backend', '-am', 'package', '-DskipTests'], root],
  ['npm', ['run', 'build'], frontend],
]) {
  const result = spawnSync(command, args, {
    cwd,
    stdio: 'inherit',
    shell: process.platform === 'win32',
    env: {
      ...process.env,
      NEXT_PUBLIC_HIDE_DEV_LOGIN: 'true',
      BUILD_TIME: '2026-06-15T10:30:00.000Z',
    },
  })
  if (result.error) throw result.error
  if (result.status !== 0) process.exit(result.status ?? 1)
}
