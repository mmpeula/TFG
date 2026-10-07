import { useEffect, useRef } from 'react'
import { hayCapaAbierta } from './useEscape'

interface Shortcuts {
  newTask?: () => void
  plan?: () => void
  board?: () => void
  pulse?: () => void
  generatePlan?: () => void
  help?: () => void
}

/** Tecla suelta -> accion. Esc no esta: cierra lo que haya abierto (ver useEscape). */
const KEYS: Record<string, keyof Shortcuts> = {
  n: 'newTask',
  p: 'plan',
  b: 'board',
  i: 'pulse',
  g: 'generatePlan',
  '?': 'help',
}

/**
 * Atajos globales de la aplicacion. No se disparan mientras se escribe en un
 * campo, para no robarle las teclas al usuario, ni con algo abierto por encima (un
 * dialogo, el panel de una tarea, un menu): una tecla ahi no deberia sacarte de la
 * pantalla que hay debajo.
 */
export function useShortcuts(shortcuts: Shortcuts) {
  const ref = useRef(shortcuts)

  useEffect(() => {
    ref.current = shortcuts
  })

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      const target = event.target as HTMLElement | null
      const typing =
        target?.tagName === 'INPUT' || target?.tagName === 'TEXTAREA' || target?.tagName === 'SELECT'
        || target?.isContentEditable
      if (typing || hayCapaAbierta() || event.metaKey || event.ctrlKey || event.altKey) return

      const name = KEYS[event.key.toLowerCase()]
      const handler = name && ref.current[name]
      if (handler) {
        event.preventDefault()
        handler()
      }
    }

    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [])
}
