/**
 * Una fecha del servidor como Date, o null si no hay o no se entiende.
 *
 * Las fechas sin hora («2026-10-06», como la fecha limite) son ese dia en la hora local.
 * {@code new Date("2026-10-06")} las lee como medianoche en UTC, que fuera de Europa
 * cae el dia anterior: la tarjeta diria una fecha y el plan la pondria en otro dia.
 */
export function toDate(value?: string | null): Date | null {
  if (!value) return null
  const date = new Date(/^\d{4}-\d{2}-\d{2}$/.test(value) ? `${value}T00:00:00` : value)
  return Number.isNaN(date.getTime()) ? null : date
}

export function formatDate(value?: string | null): string {
  return toDate(value)?.toLocaleDateString('es-ES', { day: 'numeric', month: 'short', year: 'numeric' }) ?? '—'
}

export function formatTime(value: number): string {
  return new Date(value).toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' })
}

export function formatDateTime(value?: string | null): string {
  return toDate(value)?.toLocaleString('es-ES', { dateStyle: 'short', timeStyle: 'short' }) ?? '—'
}

/** Minusculas y sin tildes, para buscar: «Diseño» encuentra «diseno» y «García», «garcia». */
export function plainText(text: string): string {
  return text.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase()
}

/** Si alguno de los textos contiene lo buscado, sin mirar mayusculas ni tildes. Sin busqueda, todo vale. */
export function matchesSearch(search: string, ...fields: (string | null | undefined)[]): boolean {
  const query = plainText(search.trim())
  return !query || fields.some((field) => field != null && plainText(field).includes(query))
}

export function initials(fullName?: string): string {
  if (!fullName) return '··'
  return fullName.split(' ').filter(Boolean).slice(0, 2).map((part) => part[0]?.toUpperCase() ?? '').join('')
}

/** Dias que faltan para una fecha (negativo si ya paso). */
export function daysUntil(value?: string | null): number | null {
  const target = toDate(value)
  if (!target) return null
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  target.setHours(0, 0, 0, 0)
  return Math.round((target.getTime() - today.getTime()) / (1000 * 60 * 60 * 24))
}

/** Dias transcurridos desde una fecha. */
export function daysSince(value?: string | null): number {
  const days = daysUntil(value)
  return days === null ? 0 : -days
}

/**
 * Minutos transcurridos desde una fecha; null si no hay fecha o no se entiende.
 *
 * Lo usan los dos textos de «hace cuanto» que tiene la aplicacion, que hasta
 * dentro de las 24 horas dicen exactamente lo mismo y a partir de ahi cambian de
 * vocabulario: la insignia va apretada y la tarjeta del workspace escribe en
 * prosa. Lo que comparten se calcula una sola vez.
 */
export function minutesSince(value?: string | null): number | null {
  if (!value) return null
  const diff = Date.now() - new Date(value).getTime()
  return Number.isNaN(diff) ? null : Math.floor(diff / 60000)
}

/** Lo que dicen los dos por igual: menos de un dia. `undefined` si ya pasa de ahi. */
export function withinTheDay(minutes: number): string | undefined {
  if (minutes < 1) return 'ahora mismo'
  if (minutes < 60) return `hace ${minutes} min`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `hace ${hours} h`
  return undefined
}

/** "hace 6 d", "hace 4 h", "ahora mismo". La version corta, para insignias. */
export function relativeTime(value?: string | null): string {
  const minutes = minutesSince(value)
  if (minutes === null) return ''
  return withinTheDay(minutes) ?? `hace ${Math.floor(minutes / 1440)} d`
}

/** Un numero con coma decimal, como se escribe en espanol. */
export function decimal(value: number, digits = 1): string {
  return value.toLocaleString('es-ES', { minimumFractionDigits: digits, maximumFractionDigits: digits })
}
