import { describe, expect, it } from 'vitest'
import { colorOf, iconOf, lastActivityLabel, WORKSPACE_COLORS } from './workspace'

// «Hoy» es el 7 de octubre de 2026 a mediodia (ver test/setup.ts).

describe('lastActivityLabel', () => {
  it.each([
    [undefined, 'sin actividad todavía'],
    ['2026-10-07T11:30:00', 'hace 30 min'],
    ['2026-10-06T10:00:00', 'ayer'],
    ['2026-09-27T12:00:00', 'hace 10 días'],
    ['2026-08-20T12:00:00', 'hace un mes'],
    ['2026-05-01T12:00:00', 'hace 5 meses'],
  ])('%s → %s', (fecha, texto) => {
    expect(lastActivityLabel(fecha)).toBe(texto)
  })
})

describe('iconOf y colorOf', () => {
  it('usa el icono elegido o las iniciales de las palabras con mas de dos letras', () => {
    expect(iconOf({ name: 'Bases de Datos', icon: ' BD ' })).toBe('BD')
    expect(iconOf({ name: 'Bases de Datos' })).toBe('BD')
    expect(iconOf({ name: 'tfg' })).toBe('T')
    expect(iconOf({ name: 'IA' })).toBe('IA')
  })

  it('usa el color elegido o uno fijo por id', () => {
    expect(colorOf({ id: 1, color: 'verde' })).toBe('#467e59')
    expect(colorOf({ id: 7 })).toBe(WORKSPACE_COLORS[1].value)
  })
})
