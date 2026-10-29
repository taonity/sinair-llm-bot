export function getAppIdentity() {
  const isStage = process.env.APP_ENVIRONMENT === 'stage'

  return {
    name: isStage ? 'Sinair LLM Bot (Stage)' : 'Sinair LLM Bot',
    iconPrefix: isStage ? '/icons/icon-stage' : '/icons/icon',
    favicon: isStage ? '/favicon-stage.ico' : '/favicon.ico',
  }
}
