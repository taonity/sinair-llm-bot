import { describe, expect, it } from 'vitest'
import { applyNewlineEffect, findEmbeddedJson } from './formatting'

describe('applyNewlineEffect', () => {
  it('renders JSON newline escapes as line breaks', () => {
    expect(applyNewlineEffect('"first\\nsecond"')).toBe('"first\nsecond"')
    expect(applyNewlineEffect('"first\\r\\nsecond"')).toBe('"first\nsecond"')
    expect(applyNewlineEffect('"first\\rsecond"')).toBe('"first\nsecond"')
  })

  it('preserves escaped backslashes followed by n or r', () => {
    expect(applyNewlineEffect('"first\\\\nsecond"')).toBe('"first\\\\nsecond"')
    expect(applyNewlineEffect('"first\\\\rsecond"')).toBe('"first\\\\rsecond"')
  })
})

describe('findEmbeddedJson', () => {
  it('pretty prints object and array string values', () => {
    expect(findEmbeddedJson('"{\\"enabled\\":true}"')).toEqual({
      formatted: '{\n  "enabled": true\n}', prefix: '', suffix: '',
    })
    expect(findEmbeddedJson('"[1,{\\"name\\":\\"test\\"}]"')?.formatted).toBe('[\n  1,\n  {\n    "name": "test"\n  }\n]')
  })

  it('finds embedded JSON surrounded by ordinary text', () => {
    expect(findEmbeddedJson('"before {\\"enabled\\":true} after"')).toEqual({
      formatted: '{\n  "enabled": true\n}', prefix: 'before ', suffix: ' after',
    })
    expect(findEmbeddedJson('"result: [1,{\\"text\\":\\"} inside string\\"}] done"')).toEqual({
      formatted: '[\n  1,\n  {\n    "text": "} inside string"\n  }\n]', prefix: 'result: ', suffix: ' done',
    })
  })

  it('ignores ordinary, malformed, and primitive string values', () => {
    expect(findEmbeddedJson('"ordinary text"')).toBeNull()
    expect(findEmbeddedJson('"{not json}"')).toBeNull()
    expect(findEmbeddedJson('"true"')).toBeNull()
  })
})