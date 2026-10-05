import { useNavigate, useParams } from 'react-router-dom'
import { Mail } from 'lucide-react'
import { HomeShell } from '@/components/shell/HomeShell'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { Skeleton } from '@/components/ui/Skeleton'
import { useInvitationMutations, useMyInvitations } from '@/hooks/useData'
import { ROLE_LABELS } from '@/lib/roles'
import { ROUTES } from '@/app/routes'

/**
 * Donde aterriza el enlace del correo de invitacion.
 *
 * No acepta sola al abrirse: entrar en un equipo es una decision, y un enlace se
 * abre por muchos motivos (comprobar que es, un cliente de correo que precarga
 * los enlaces...). Se ensena de que va y se decide aqui.
 */
export function InvitationScreen() {
  const { token = '' } = useParams()
  const navigate = useNavigate()
  const { data: invitaciones, isPending } = useMyInvitations()
  const { accept, reject } = useInvitationMutations()

  const invitacion = invitaciones?.find((item) => item.token === token)

  return (
    <HomeShell>
      {isPending ? (
        <Skeleton label="Buscando la invitación…" lines={2} />
      ) : !invitacion ? (
        <EmptyState
          icon={<Mail size={22} strokeWidth={2.75} />}
          title="Esta invitación ya no está disponible"
          body="O la respondiste, o la cancelaron, o es de otra cuenta. Si esperabas entrar en un equipo, pide que te vuelvan a invitar."
          action={<Button onClick={() => navigate(ROUTES.workspaces)}>Ir a mis espacios de trabajo</Button>}
        />
      ) : (
        <EmptyState
          icon={<Mail size={22} strokeWidth={2.75} />}
          title={`Te han invitado a ${invitacion.teamName}`}
          body={`${invitacion.invitedByName ?? 'Alguien'} te ha invitado como ${ROLE_LABELS[invitacion.role].toLowerCase()}. Si aceptas, entrarás en el equipo y verás su tablero.`}
          action={
            <>
              <Button
                variant="secondary"
                disabled={reject.isPending}
                onClick={() => reject.mutateAsync(token).then(() => navigate(ROUTES.workspaces))}
              >
                Rechazar
              </Button>
              <Button
                disabled={accept.isPending}
                onClick={() => accept.mutateAsync(token).then(() => navigate(ROUTES.workspaces))}
              >
                {accept.isPending ? 'Entrando…' : 'Aceptar invitación'}
              </Button>
            </>
          }
        />
      )}
    </HomeShell>
  )
}
