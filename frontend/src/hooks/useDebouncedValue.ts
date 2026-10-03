import { useEffect, useState } from 'react'

/** El valor, pero solo despues de que haya dejado de cambiar durante {@code delayMs}: para no consultar en cada tecla. */
export function useDebouncedValue<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs)
    return () => clearTimeout(timer)
  }, [value, delayMs])
  return debounced
}
