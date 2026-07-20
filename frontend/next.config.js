const { execSync } = require('child_process')

function git(command) {
  try {
    return execSync(`git ${command}`, { encoding: 'utf8' }).trim()
  } catch {
    return 'unknown'
  }
}

/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  output: 'standalone',
  // Pin the workspace root to this app so the root-level package-lock.json (Maven monorepo)
  // doesn't make Next.js infer the wrong root when multiple lockfiles are present.
  turbopack: {
    root: __dirname,
  },
  env: {
    BUILD_TIME: new Date().toISOString(),
    GIT_COMMIT_SHA: process.env.GIT_COMMIT_SHA || git('rev-parse HEAD'),
    GIT_COMMIT_SHORT: process.env.GIT_COMMIT_SHORT || git('rev-parse --short HEAD'),
    GIT_BRANCH: process.env.GIT_BRANCH || git('rev-parse --abbrev-ref HEAD'),
  },
}
module.exports = nextConfig
