import { useCallback, useSyncExternalStore } from 'react'

/**
 * Media query reactiva.
 *
 * Se usa para decidir en JS lo que no se puede resolver solo con CSS: en movil el
 * tablero muestra una columna cada vez y las tarjetas se mueven con el menu
 * "Mover a…" en lugar de arrastrando.
 */
export function useMediaQuery(query: string): boolean {
  const subscribe = useCallback(
    (onChange: () => void) => {
      const media = window.matchMedia(query)
      media.addEventListener('change', onChange)
      return () => media.removeEventListener('change', onChange)
    },
    [query],
  )

  return useSyncExternalStore(
    subscribe,
    () => window.matchMedia(query).matches,
    () => false,
  )
}
