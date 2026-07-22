import PayloadViewer from '@/features/payload-viewer/PayloadViewer'

type Context = { params: Promise<{ runId: string; index: string }> }

export default async function PayloadPage({ params }: Context) {
  const { runId, index } = await params
  return <PayloadViewer runId={runId} index={index} />
}