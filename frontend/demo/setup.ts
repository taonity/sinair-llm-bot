import { chromium, expect } from '@playwright/test'
import { mkdir } from 'node:fs/promises'

export default async function setup() {
  await mkdir('test-results', { recursive: true })
  const browser = await chromium.launch()
  try {
    const page = await browser.newPage()
    await page.goto('http://127.0.0.1:8080/oauth2/authorization/google-sinair-llm-bot')
    await expect(page).toHaveURL('http://127.0.0.1:3000/')
    await expect(page.getByRole('tab', { name: 'Admin', exact: true })).toBeVisible()
    await expect(page.getByText('alice.morgan@example.com', { exact: true })).toBeVisible()
    await page.context().storageState({ path: 'test-results/demo-owner.json' })
  } finally {
    await browser.close()
  }
}
