export interface BuildInfo {
  app: {
    name: string
    environment: string
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
      name: 'sinair-llm-bot-frontend',
      // Deployment environment is a runtime concern (the same image runs in stage and prod),
      // so it is read from APP_ENVIRONMENT at request time rather than inlined at build time
      // like the git/build fields. Defaults to 'local' for local dev.
      environment: process.env.APP_ENVIRONMENT || 'local',
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
