import { useEffect, useRef } from 'react'

interface Shortcuts {
  newTask?: () => void
  plan?: () => void
  board?: () => void
  pulse?: () => void
  generatePlan?: () => void
  help?: () => void
  escape?: () => void
}

/** Tecla suelta -> accion. Esc se trata aparte porque no lleva la misma logica. */
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
 * campo, para no robarle las teclas al usuario.
 */
export function useShortcuts(shortcuts: Shortcuts) {
  const ref = useRef(shortcuts)

  useEffect(() => {
    ref.current = shortcuts
  })

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      const handlers = ref.current
      const target = event.target as HTMLElement | null
      const typing =
        target?.tagName === 'INPUT' || target?.tagName === 'TEXTAREA' || target?.isContentEditable

      if (event.key === 'Escape') {
        handlers.escape?.()
        return
      }

      if (typing || event.metaKey || event.ctrlKey || event.altKey) return

      const name = KEYS[event.key.toLowerCase()]
      const handler = name && handlers[name]
      if (handler) {
        event.preventDefault()
        handler()
      }
    }

    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [])
}
