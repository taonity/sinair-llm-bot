import { expect, test } from '@playwright/test'
import { createScreenshotCapture } from '@taonity/demo-screenshots'

const capture = createScreenshotCapture(expect)

const scenes = [
  { name: 'messages', tab: 'Messages', ready: 'Alice Morgan' },
  { name: 'events', tab: 'Events', ready: 'Sophia Patel' },
  { name: 'outbound', tab: 'Outbound', ready: 'the receipt wins this argument.' },
  { name: 'pipelines', tab: 'Pipelines', ready: 'Alice Morgan' },
  { name: 'summaries', tab: 'Summaries', ready: '#general' },
  { name: 'config', tab: 'Config', ready: 'Search settings' },
  { name: 'admin', tab: 'Admin', ready: 'Pending access requests' },
  { name: 'about', tab: 'About', ready: 'Backend' },
  { name: 'pipeline-details', tab: 'Pipelines', ready: 'Percentage claim addressed to yarik', query: 'tab=pipelines&pipeline=demo-pipeline-01' },
  { name: 'config-persona', tab: 'Config', ready: 'yarik', persona: true },
  { name: 'llm-payloads', payload: true },
] as const

for (const scene of scenes) {
  test(scene.name, async ({ page, baseURL }, testInfo) => {
    const failures: string[] = []
    page.on('pageerror', (error) => failures.push(error.message))
    page.on('response', (response) => {
      if (response.url().startsWith(`${baseURL}/api/`) && response.status() >= 400) {
        failures.push(`${response.status()} ${response.url()}`)
      }
    })
    await page.clock.setFixedTime(new Date('2026-06-15T10:30:00Z'))
    await page.addInitScript((theme) => localStorage.setItem('theme', theme), testInfo.project.use.colorScheme === 'dark' ? 'dark' : 'light')
    await page.route('**/api/actuator/{info,backend}', async (route) => {
      const response = await route.fetch()
      const data = await response.json()
      if (data.build?.time) data.build.time = '2026-06-15T10:30:00.000Z'
      if (data.runtime) data.runtime.uptime = 3600
      await route.fulfill({ response, json: data })
    })
    if ('payload' in scene) {
      if (testInfo.project.name.startsWith('desktop')) await page.setViewportSize({ width: 1232, height: 1000 })
      await page.goto('/view/payload/demo-pipeline-01/0')
      await expect(page.getByText('LLM Payloads', { exact: true })).toBeVisible()
      await expect(page.getByText('Request', { exact: true })).toBeVisible()
      await expect(page.getByText('Response', { exact: true })).toBeVisible()
      await expect(page.getByText('demo/group-chat', { exact: false }).first()).toBeVisible()
      const request = await page.request.get('/api/console/pipeline-runs/demo-pipeline-01/llm-usage/0/request')
      const response = await page.request.get('/api/console/pipeline-runs/demo-pipeline-01/llm-usage/0/response')
      expect(request.ok()).toBe(true)
      expect(response.ok()).toBe(true)
      expect((await request.json()).messages.at(-1).content).toContain('20% tax')
      expect((await request.json()).messages.at(-1).content).toContain('@yarik')
      expect((await response.json()).choices[0].message.content).toContain('the receipt wins this argument.')
      if (testInfo.project.name.startsWith('phone')) {
        await page.addStyleTag({ content: `
          .payload-viewer { position: relative !important; height: auto !important; min-height: 100vh; }
          .payload-viewer > .grid { flex: none !important; grid-template-rows: auto auto !important; overflow: visible !important; }
          .payload-viewer > .grid > .flex { overflow: visible !important; }
          .payload-viewer > .grid > .flex > .overflow-auto { flex: none !important; overflow: visible !important; }
        ` })
        for (const region of await page.getByRole('region', { name: 'Read-only JSON payload' }).all()) {
          const dimensions = await region.evaluate((node) => ({ content: node.parentElement!.scrollHeight, visible: node.parentElement!.clientHeight }))
          expect(dimensions.content).toBeLessThanOrEqual(dimensions.visible + 1)
        }
        expect(await page.locator('.payload-viewer').evaluate((node) => node.scrollHeight)).toBeGreaterThan(844)
      }
      expect(await page.locator('.payload-viewer').innerText()).not.toMatch(/\p{Script=Cyrillic}|\p{Script=Han}|\p{Script=Arabic}/u)
      await page.evaluate(() => document.fonts.ready)
      await page.mouse.move(0, 0)
      expect(failures).toEqual([])
      await capture(page, testInfo, scene.name)
      return
    }
    const query = 'query' in scene ? scene.query : `tab=${scene.name.split('-')[0]}`
    await page.goto(`/?${query}`)
    await page.addStyleTag({ content: '*, *::before, *::after { animation: none !important; transition: none !important; }' })
    await expect(page.getByRole('tab', { name: scene.tab, exact: true })).toHaveAttribute('aria-selected', 'true')
    const panel = page.getByRole('tabpanel')
    if ('persona' in scene) {
      await panel.getByRole('textbox', { name: 'Search settings', exact: true }).fill('app.bot.persona.')
    }
    if (scene.name === 'config') {
      await expect(panel.getByRole('textbox', { name: scene.ready })).toBeVisible()
    } else if ('persona' in scene) {
      const nickname = panel.getByRole('textbox', { name: 'name', exact: true })
      await expect(nickname).toBeVisible()
      await expect(nickname).toHaveValue('yarik')
    } else {
      await expect(panel.getByText(scene.ready, { exact: false }).first()).toBeVisible()
    }
    await expect(panel.locator('[aria-busy="true"]')).toHaveCount(0)
    await expect(panel.locator('[data-slot="skeleton"]')).toHaveCount(0)
    await expect(page.locator('.error-notification')).toHaveCount(0)
    if (scene.name === 'outbound') {
      const first = panel.locator('.outbound-records .record-row').first()
      await expect(first.locator('td[data-column="createdAt"]')).toContainText('Created')
      await expect(first.locator('td[data-column="sentAt"]')).toContainText('Sent')
      await expect(first.locator('td[data-column="createdAt"] time')).toHaveAttribute('title', /Created: .*10:24:04/)
      await expect(first.locator('td[data-column="sentAt"] time')).toHaveAttribute('title', /Sent: .*10:24:05/)
    }
    if (scene.name === 'about') await expect(panel.getByText('online', { exact: true })).toHaveCount(2)
    await page.evaluate(async () => {
      await document.fonts.ready
      await Promise.all(Array.from(document.images, (image) => image.decode().catch(() => undefined)))
    })
    const content = await panel.evaluate((element) => [
      (element as HTMLElement).innerText,
      ...Array.from(element.querySelectorAll('input, textarea'), (input) => (input as HTMLInputElement).value),
    ].join('\n'))
    expect(content).not.toMatch(/\p{Script=Cyrillic}|\p{Script=Han}|\p{Script=Arabic}/u)
    expect(content).not.toMatch(/\bsegfault\b|@seg\b/i)
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(testInfo.project.use.viewport!.width)
    expect(failures).toEqual([])
    await page.evaluate(() => {
      (document.activeElement as HTMLElement | null)?.blur()
      window.scrollTo(0, 0)
    })
    await page.mouse.move(0, 0)
    if (testInfo.project.name.startsWith('desktop')) {
      const clip = await page.locator('.console-shell > div').first().evaluate((element) => {
        const bounds = element.getBoundingClientRect()
        return {
          x: Math.floor(bounds.left) - 16,
          y: 0,
          width: Math.ceil(bounds.width) + 32,
          height: Math.max(document.body.scrollHeight, document.documentElement.scrollHeight, window.innerHeight),
        }
      })
      await capture(page, testInfo, scene.name, { clip })
    } else {
      await capture(page, testInfo, scene.name)
    }
  })
}
