import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  daysSince, daysUntil, decimal, initials, matchesSearch, plainText, relativeTime, toDate, withinTheDay,
} from './format'

describe('toDate', () => {
  it('lee una fecha sin hora como medianoche local, no UTC', () => {
    const date = toDate('2026-10-06')!
    expect([date.getFullYear(), date.getMonth(), date.getDate(), date.getHours()]).toEqual([2026, 9, 6, 0])
  })

  it('devuelve null si no hay fecha o no se entiende', () => {
    expect(toDate(undefined)).toBeNull()
    expect(toDate('')).toBeNull()
    expect(toDate('no es una fecha')).toBeNull()
  })
})

describe('busqueda sin tildes ni mayusculas', () => {
  it('plainText quita tildes y pasa a minusculas', () => {
    expect(plainText('Diseño García')).toBe('diseno garcia')
  })

  it('matchesSearch encuentra en cualquiera de los campos', () => {
    expect(matchesSearch('garcia', 'Lucía', 'Pablo García')).toBe(true)
    expect(matchesSearch('ortega', 'Lucía', null, undefined)).toBe(false)
  })

  it('sin busqueda todo vale', () => {
    expect(matchesSearch('   ', 'lo que sea')).toBe(true)
  })
})

describe('initials', () => {
  it('toma las dos primeras palabras', () => {
    expect(initials('lucía fernández gil')).toBe('LF')
  })

  it('sin nombre devuelve el marcador', () => {
    expect(initials()).toBe('··')
  })
})

describe('fechas relativas', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-10-07T12:00:00'))
  })
  afterEach(() => vi.useRealTimers())

  it('daysUntil cuenta dias de calendario, negativo si ya paso', () => {
    expect(daysUntil('2026-10-10')).toBe(3)
    expect(daysUntil('2026-10-07')).toBe(0)
    expect(daysUntil('2026-10-05')).toBe(-2)
    expect(daysUntil(null)).toBeNull()
  })

  it('daysSince es el contrario y 0 sin fecha', () => {
    expect(daysSince('2026-10-01')).toBe(6)
    expect(daysSince(undefined)).toBe(0)
  })

  it('relativeTime usa minutos, horas y dias', () => {
    expect(relativeTime('2026-10-07T11:59:40')).toBe('ahora mismo')
    expect(relativeTime('2026-10-07T11:45:00')).toBe('hace 15 min')
    expect(relativeTime('2026-10-07T08:00:00')).toBe('hace 4 h')
    expect(relativeTime('2026-10-01T12:00:00')).toBe('hace 6 d')
    expect(relativeTime(null)).toBe('')
  })

  it('withinTheDay no dice nada a partir de 24 h', () => {
    expect(withinTheDay(24 * 60)).toBeUndefined()
  })
})

describe('decimal', () => {
  it('usa coma decimal', () => {
    expect(decimal(1.25, 2)).toBe('1,25')
    expect(decimal(3)).toBe('3,0')
  })
})
