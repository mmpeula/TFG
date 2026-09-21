import { Mail } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import styles from './InvitationsBanner.module.css'
import { useInvitationMutations, useMyInvitations } from '@/hooks/useData'
import { ROLE_LABELS } from '@/lib/roles'

/**
 * Las invitaciones que esperan respuesta, en la propia lista de workspaces.
 *
 * El correo con el enlace es el aviso, pero no puede ser el unico camino: si no
 * llega, o se borra, la invitacion quedaria enterrada. Aqui esta siempre a la
 * vista mientras no se responda.
 */
export function InvitationsBanner() {
  const { data: invitaciones } = useMyInvitations()
  const { accept, reject } = useInvitationMutations()

  if (!invitaciones || invitaciones.length === 0) return null

  return (
    <section className={styles.wrap} aria-label="Invitaciones pendientes">
      {invitaciones.map((invitacion) => (
        <div key={invitacion.token} className={styles.card}>
          <span className={styles.icon} aria-hidden="true">
            <Mail size={15} strokeWidth={2.75} />
          </span>
          <p className={styles.text}>
            {invitacion.invitedByName ?? 'Alguien'} te ha invitado a{' '}
            <b>{invitacion.teamName}</b> como {ROLE_LABELS[invitacion.role].toLowerCase()}.
          </p>
          <div className={styles.actions}>
            <Button
              variant="secondary"
              small
              disabled={reject.isPending}
              onClick={() => reject.mutate(invitacion.token)}
            >
              Rechazar
            </Button>
            <Button small disabled={accept.isPending} onClick={() => accept.mutate(invitacion.token)}>
              Aceptar
            </Button>
          </div>
        </div>
      ))}
    </section>
  )
}
