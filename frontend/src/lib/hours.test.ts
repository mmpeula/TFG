import { describe, expect, it } from 'vitest'
import { formatHours, parseHours } from './hours'

describe('parseHours', () => {
  it.each([
    ['1,5', 1.5],
    ['1.5', 1.5],
    ['2 h', 2],
    ['3horas', 3],
    [' 0,5 ', 0.5],
    ['1,239', 1.24],
  ])('«%s» son %s horas', (raw, hours) => {
    expect(parseHours(raw)).toBe(hours)
  })

  it.each(['', '0', 'abc', '-2', '1,5,5', '10000'])('«%s» no es valido', (raw) => {
    expect(parseHours(raw)).toBeNull()
  })
})

describe('formatHours', () => {
  it('escribe con coma y la unidad', () => {
    expect(formatHours(1.5)).toBe('1,5 h')
    expect(formatHours(2)).toBe('2 h')
  })
})
