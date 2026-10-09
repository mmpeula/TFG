import { useLocation } from 'react-router-dom'

/** Ruta en la que esta la app en cada momento, para comprobar las navegaciones (ver currentPath). */
export function Where() {
  const { pathname } = useLocation()
  return <span data-testid="ruta" hidden>{pathname}</span>
}
