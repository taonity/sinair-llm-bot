export function applyNewlineEffect(value: string): string {
  let result = ''

  for (let index = 0; index < value.length; index += 1) {
    if (value[index] !== '\\') {
      result += value[index]
      continue
    }

    let slashCount = 1
    while (value[index + slashCount] === '\\') slashCount += 1
    const escape = value[index + slashCount]
    if (slashCount % 2 === 1 && (escape === 'n' || escape === 'r')) {
      result += '\\'.repeat(slashCount - 1)
      if (escape === 'r' && value.slice(index + slashCount + 1, index + slashCount + 3) === '\\n') {
        index += slashCount + 2
      } else {
        index += slashCount
      }
      result += '\n'
      continue
    }

    result += '\\'.repeat(slashCount)
    index += slashCount - 1
  }

  return result
}

export interface EmbeddedJsonMatch {
  formatted: string
  prefix: string
  suffix: string
}

function closingIndex(value: string, start: number): number | null {
  const closingForOpening: Record<string, string> = { '{': '}', '[': ']' }
  const openings: string[] = []
  let inString = false
  let escaped = false

  for (let index = start; index < value.length; index += 1) {
    const char = value[index] ?? ''
    if (inString) {
      if (escaped) escaped = false
      else if (char === '\\') escaped = true
      else if (char === '"') inString = false
      continue
    }

    if (char === '"') inString = true
    else if (closingForOpening[char]) openings.push(char)
    else if (char === '}' || char === ']') {
      const opening = openings.pop()
      if (!opening || closingForOpening[opening] !== char) return null
      if (!openings.length) return index
    }
  }

  return null
}

export function findEmbeddedJson(value: string): EmbeddedJsonMatch | null {
  try {
    const decoded = JSON.parse(value)
    if (typeof decoded !== 'string') return null

    for (let start = 0; start < decoded.length; start += 1) {
      if (decoded[start] !== '{' && decoded[start] !== '[') continue
      const end = closingIndex(decoded, start)
      if (end === null) continue

      try {
        const parsed = JSON.parse(decoded.slice(start, end + 1))
        if (parsed !== null && typeof parsed === 'object') {
          return {
            formatted: JSON.stringify(parsed, null, 2),
            prefix: decoded.slice(0, start),
            suffix: decoded.slice(end + 1),
          }
        }
      } catch {
        continue
      }
    }

    return null
  } catch {
    return null
  }
}