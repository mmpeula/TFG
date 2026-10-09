import { useEffect, useRef } from 'react'

/**
 * Lo que se cierra con Esc, de lo mas reciente a lo mas antiguo: un desplegable dentro
 * del panel de una tarea, una confirmacion encima de ese panel, el chat de ayuda… Cada
 * Esc cierra solo lo de arriba. Si cada cosa escuchara la tecla por su cuenta, un Esc en
 * «Borrar la tarea» cerraria tambien el panel que hay debajo.
 */
const capas: Array<() => void> = []

function alPulsar(event: KeyboardEvent) {
  if (event.key === 'Escape' && capas.length > 0) capas[capas.length - 1]()
}

/** Cierra esta capa con Esc mientras {@code active}; la ultima en abrirse es la primera en cerrarse. */
export function useEscape(onEscape: () => void, active = true) {
  const latest = useRef(onEscape)
  useEffect(() => {
    latest.current = onEscape
  })

  useEffect(() => {
    if (!active) return
    const capa = () => latest.current()
    capas.push(capa)
    if (capas.length === 1) window.addEventListener('keydown', alPulsar)
    return () => {
      capas.splice(capas.lastIndexOf(capa), 1)
      if (capas.length === 0) window.removeEventListener('keydown', alPulsar)
    }
  }, [active])
}
