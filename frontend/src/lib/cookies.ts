function escapeCookieName(name: string) {
  return name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

export function getCookie(name: string) {
  if (typeof document === 'undefined') {
    return null
  }

  const match = document.cookie.match(
    new RegExp(`(?:^|; )${escapeCookieName(name)}=([^;]*)`),
  )

  return match && match[1] !== undefined ? decodeURIComponent(match[1]) : null
}

export function deleteCookie(name: string) {
  if (typeof document === 'undefined') {
    return
  }

  document.cookie = `${name}=; Path=/; Expires=Thu, 01 Jan 1970 00:00:01 GMT;`
}
