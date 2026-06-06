export interface BuildInfo {
  app: {
    name: string
  }
  git: {
    commit: string
    commitShort: string
    branch: string
  }
  build: {
    time: string
    nodeVersion: string
  }
  runtime: {
    env: string
    uptime: number
  }
}

export function getBuildInfo(): BuildInfo {
  return {
    app: {
      name: 'fullstack-starter-frontend',
    },
    git: {
      commit: process.env.GIT_COMMIT_SHA || 'unknown',
      commitShort: process.env.GIT_COMMIT_SHORT || 'unknown',
      branch: process.env.GIT_BRANCH || 'unknown',
    },
    build: {
      time: process.env.BUILD_TIME || 'unknown',
      nodeVersion: process.version,
    },
    runtime: {
      env: process.env.NODE_ENV || 'unknown',
      uptime: Math.floor(process.uptime()),
    },
  }
}
