import type { ReactNode } from 'react'
import { Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import styles from './Auth.module.css'
import logo from '@/assets/logo.png'
import { toErrorMessage } from '@/lib/api-client'

/** El marco de las dos pantallas de entrada: la marca arriba, la tarjeta y el enlace a la otra. */
export function AuthLayout({
  claim,
  title,
  subtitle,
  foot,
  children,
}: {
  /** Frase bajo la marca, si la pantalla la lleva. */
  claim?: string
  title: string
  subtitle: string
  /** El enlace a la otra pantalla («¿No tienes cuenta? Regístrate»). */
  foot: ReactNode
  children: ReactNode
}) {
  return (
    <div className={styles.page}>
      <div className={styles.wrap}>
        <div className={styles.brand}>
          <span className={styles.mark}><img src={logo} alt="" /></span>
          <span className={styles.name}>Planif<span className={styles.ia}>IA</span></span>
          {claim && <span className={styles.claim}>{claim}</span>}
        </div>

        <div className={styles.card}>
          <h1 className={styles.title}>{title}</h1>
          <p className={styles.subtitle}>{subtitle}</p>
          {children}
        </div>

        <p className={styles.foot}>{foot}</p>
      </div>
    </div>
  )
}

/** El error de la ultima vez, si lo hubo, y el boton de enviar con su rueda mientras espera. */
export function AuthSubmit({
  error,
  pending,
  disabled,
  children,
}: {
  error: unknown
  pending: boolean
  disabled?: boolean
  children: ReactNode
}) {
  return (
    <>
      {error != null && <div className={styles.error}>{toErrorMessage(error)}</div>}
      <Button type="submit" block disabled={pending || disabled}>
        {pending && <Loader2 size={14} strokeWidth={2.75} />}
        {children}
      </Button>
    </>
  )
}
