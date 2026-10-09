import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, beforeEach, vi } from 'vitest'
import { queryClient } from '@/app/query-client'
import { resetServer } from './server'

/*
 * Lo que jsdom no trae y la app usa. Se define una vez para todos los tests:
 * matchMedia (tablero en movil), URLs de ficheros (fotos) y scrollIntoView (chat).
 */
let mobile = false
/** Hace que las media queries de ancho maximo se cumplan, como en un movil. */
export function setMobile(value: boolean) {
  mobile = value
}

Object.defineProperty(window, 'matchMedia', {
  configurable: true,
  value: (query: string) => ({
    matches: mobile && query.includes('max-width'),
    media: query,
    addEventListener: () => {},
    removeEventListener: () => {},
  }),
})
URL.createObjectURL = vi.fn(() => 'blob:foto')
URL.revokeObjectURL = vi.fn()
Element.prototype.scrollIntoView = vi.fn()

beforeEach(() => {
  // «Hoy» es siempre el 7 de octubre de 2026 a mediodia: los avisos de vencimiento, los
  // sprints y las fechas relativas no pueden depender del dia en que se pasen los tests.
  // Solo se falsea Date: los temporizadores siguen siendo reales para React Query.
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date('2026-10-07T12:00:00'))
  resetServer()
  localStorage.clear()
  mobile = false
})

// Cada test empieza con el DOM vacio y sin nada en la cache de React Query.
afterEach(() => {
  cleanup()
  queryClient.clear()
  vi.useRealTimers()
})
