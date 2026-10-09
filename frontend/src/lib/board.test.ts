import { describe, expect, it } from 'vitest'
import { task, user } from '@/test/factories'
import { dropPosition, matchesBoardFilters, SIN_ASIGNAR } from './board'

describe('dropPosition', () => {
  const columna = [task({ id: 1 }), task({ id: 2 }), task({ id: 3 })]

  it('al soltar en el hueco va al final, sin contar la propia tarea', () => {
    expect(dropPosition(columna, 2)).toBe(2)
    expect(dropPosition(columna, 99)).toBe(3)
  })

  it('al soltar sobre una tarjeta ocupa su sitio', () => {
    expect(dropPosition(columna, 3, 1)).toBe(0)
    expect(dropPosition(columna, 1, 3)).toBe(1)
  })

  it('si la tarjeta destino no esta, va al final', () => {
    expect(dropPosition(columna, 1, 42)).toBe(2)
  })
})

describe('matchesBoardFilters', () => {
  const lucia = user(5, 'Lucía')
  const base = task({ id: 13302, title: 'Diseñar la base de datos', assignees: [lucia], type: 'HISTORIA' })

  it('sin filtros pasa todo', () => {
    expect(matchesBoardFilters(base, '', [])).toBe(true)
  })

  it('busca en el titulo sin tildes', () => {
    expect(matchesBoardFilters(base, 'disenar', [])).toBe(true)
    expect(matchesBoardFilters(base, 'frontend', [])).toBe(false)
  })

  it('busca por numero con o sin almohadilla', () => {
    expect(matchesBoardFilters(base, '#13302', [])).toBe(true)
    expect(matchesBoardFilters(base, '133', [])).toBe(true)
    expect(matchesBoardFilters(base, '#999', [])).toBe(false)
  })

  it('filtra por persona y por «sin asignar»', () => {
    expect(matchesBoardFilters(base, '', [5])).toBe(true)
    expect(matchesBoardFilters(base, '', [6])).toBe(false)
    expect(matchesBoardFilters(task({ assignees: [] }), '', [SIN_ASIGNAR])).toBe(true)
    expect(matchesBoardFilters(base, '', [SIN_ASIGNAR])).toBe(false)
  })

  it('filtra por tipo; sin tipo cuenta como «Tarea»', () => {
    expect(matchesBoardFilters(base, '', [], ['HISTORIA', 'BUG'])).toBe(true)
    expect(matchesBoardFilters(base, '', [], ['BUG'])).toBe(false)
    const antigua = task({ type: undefined as never })
    expect(matchesBoardFilters(antigua, '', [], ['TAREA'])).toBe(true)
  })

  it('los filtros se combinan', () => {
    expect(matchesBoardFilters(base, 'base', [5], ['HISTORIA'])).toBe(true)
    expect(matchesBoardFilters(base, 'base', [6], ['HISTORIA'])).toBe(false)
  })
})
