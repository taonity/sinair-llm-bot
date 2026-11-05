import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import { OutboundTimestamp } from './OutboundTimestamp'

describe('outbound timestamps', () => {
  it('identifies creation and delivery separately and preserves seconds', () => {
    const created = renderToStaticMarkup(createElement(OutboundTimestamp, { value: '2026-06-15T10:24:04', label: 'Created' }))
    const sent = renderToStaticMarkup(createElement(OutboundTimestamp, { value: '2026-06-15T10:24:05', label: 'Sent', createdAt: '2026-06-15T10:24:04' }))
    expect(created).toContain('Created: ')
    expect(created).toContain('10:24:04')
    expect(sent).toContain('Sent: ')
    expect(sent).toContain('10:24:05')
    expect(sent).toContain('>10:24:05</span>')
  })

  it('retains the delivery date when the message crosses midnight', () => {
    const html = renderToStaticMarkup(createElement(OutboundTimestamp, { value: '2026-06-16T00:00:02', label: 'Sent', createdAt: '2026-06-15T23:59:59' }))
    expect(html).not.toContain('>00:00:02</span>')
    expect(html).toContain('dateTime="2026-06-16T00:00:02"')
  })

  it('does not invent a delivery timestamp for an unsent message', () => {
    const html = renderToStaticMarkup(createElement(OutboundTimestamp, { value: null, label: 'Sent' }))
    expect(html).toContain('Not sent')
    expect(html).not.toContain('<time')
  })
})
