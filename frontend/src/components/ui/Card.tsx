import type { ReactNode } from 'react'
import { Info } from 'lucide-react'
import styles from './Card.module.css'

/**
 * Marco de una pantalla: una sola tarjeta con borde, y dentro sus apartados
 * ({@link Card}) uno debajo de otro, separados por una linea. Asi una pantalla
 * de ajustes o de formulario es un bloque, no una pila de cajas sueltas.
 */
export function CardGroup({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={[styles.group, className].filter(Boolean).join(' ')}>{children}</div>
}

/**
 * Un apartado de un {@link CardGroup}: titulo y una linea que explica para que
 * sirve a la izquierda, y el control a la derecha (un boton, un campo, una zona
 * de subida). Lo que no cabe en una fila —una lista, una tabla— va debajo, a
 * todo el ancho, como children.
 *
 * La columna del titulo tiene ancho fijo para que los controles de todos los
 * apartados empiecen en la misma vertical.
 */
export function Card({
  title,
  description,
  aside,
  children,
  className,
}: {
  title: ReactNode
  description?: ReactNode
  /** Control de la derecha. Si quiere ocupar todo el hueco, que lleve flex: 1. */
  aside?: ReactNode
  children?: ReactNode
  className?: string
}) {
  return (
    <section className={[styles.card, className].filter(Boolean).join(' ')}>
      <div className={styles.top}>
        <div className={[styles.head, !aside && styles.headWide].filter(Boolean).join(' ')}>
          <h2 className={styles.title}>{title}</h2>
          {description && <p className={styles.description}>{description}</p>}
        </div>
        {aside && <div className={styles.aside}>{aside}</div>}
      </div>
      {children && <div className={styles.body}>{children}</div>}
    </section>
  )
}

/**
 * Aclaracion de como funciona algo, al pie de un apartado: el «i» la separa del
 * contenido. El tono `accent` (azul) es solo para lo que hace la IA.
 */
export function InfoNote({ children, tone = 'neutral' }: { children: ReactNode; tone?: 'neutral' | 'accent' }) {
  return (
    <p className={[styles.info, tone === 'accent' && styles.infoAccent].filter(Boolean).join(' ')}>
      <Info className={styles.infoIcon} size={15} strokeWidth={2.25} aria-hidden="true" />
      <span>{children}</span>
    </p>
  )
}
