import _ from 'lodash'

function keysToCamel(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map((item) => keysToCamel(item))
  }

  if (value !== null && typeof value === 'object' && value.constructor === Object) {
    return Object.fromEntries(
      Object.entries(value).map(([key, nestedValue]) => [
        _.camelCase(key),
        keysToCamel(nestedValue),
      ]),
    )
  }

  return value
}

export default keysToCamel
