import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ChevronDown, UserMinus, UserPlus, Users, X } from 'lucide-react'
import { WorkspaceShell } from '@/components/shell/WorkspaceShell'
import { Avatar } from '@/components/ui/Avatar'
import { Button } from '@/components/ui/Button'
import { Card, CardGroup, InfoNote } from '@/components/ui/Card'
import { Tag } from '@/components/ui/Tag'
import { Dropdown, DropdownItem } from '@/components/ui/Controls'
import { EmptyState } from '@/components/ui/EmptyState'
import { Skeleton } from '@/components/ui/Skeleton'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import styles from './MembersScreen.module.css'
import {
  useInvitationMutations, useInviteCandidate, useMemberMutations, useTeamInvitations, useTeamMembers, useWorkspace,
} from '@/hooks/useData'
import { useAuth } from '@/state/context'
import { canManage, ROLE_LABELS } from '@/lib/roles'
import { relativeTime } from '@/lib/format'
import { ROUTES } from '@/app/routes'
import { useDebouncedValue } from '@/hooks/useDebouncedValue'
import type { Role } from '@/types/api'

/**
 * A que puede cambiarse el rol de alguien que trabaja en el equipo: alumno o
 * responsable. Docente no entra aqui, ni de ida ni de vuelta: ese papel se elige
 * al invitar (ver el formulario de invitar) y el servidor no deja cambiarlo
 * despues. Por eso un docente no tiene menu de rol, solo su etiqueta.
 */
const ROLES_DE_TRABAJO: Role[] = ['MIEMBRO', 'RESPONSABLE']

/**
 * Quien trabaja en este workspace y que puede hacer cada uno.
 *
 * Por debajo sigue siendo la gestion de miembros del equipo, con las mismas
 * reglas de siempre: el rol se asigna por equipo y manda sobre todo el tablero.
 */
export function MembersScreen() {
  const { workspaceId: param } = useParams()
  const workspaceId = Number(param)
  const navigate = useNavigate()
  const { user } = useAuth()

  const { data: workspace, isPending } = useWorkspace(workspaceId)
  const { data: members } = useTeamMembers(workspace?.teamId)
  const { data: pendientes } = useTeamInvitations(workspace?.teamId)
  const mutations = useMemberMutations(workspace?.teamId)
  const invitaciones = useInvitationMutations(workspace?.teamId)

  const [email, setEmail] = useState('')
  // Solo se pregunta por la cuenta cuando el correo esta completo y se ha dejado de teclear.
  const correo = useDebouncedValue(email.trim().toLowerCase(), 400)
  const correoValido = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(correo) && correo === email.trim().toLowerCase()
  const { data: candidato, isFetching: buscandoCuenta } = useInviteCandidate(
    workspace?.teamId, correoValido ? correo : undefined,
  )
  // La eleccion (docente o alumno) solo existe si la cuenta admite las dos, es decir, si es de docente.
  const puedeElegir = correoValido && (candidato?.allowedRoles.length ?? 0) > 1
  const [eleccion, setEleccion] = useState<{ correo: string; rol: Role } | null>(null)
  // Sin rol marcado de antemano: con una cuenta docente hay que elegir a mano, para que
  // nadie entre como docente (o como alumno) solo por no haber visto la pregunta. Una
  // eleccion hecha para otro correo tampoco se arrastra.
  const rolElegido = eleccion?.correo === correo ? eleccion.rol : undefined
  // Mientras no se sepa que cuenta es (aun tecleando, o esperando al servidor) no se envia:
  // si no, pegar el correo y pulsar Invitar en el acto mandaba la invitacion antes de que
  // saliera la pregunta, con el rol por defecto.
  const correoEscrito = email.trim().toLowerCase()
  const comprobando = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(correoEscrito)
    && (correo !== correoEscrito || buscandoCuenta)
  const faltaElegir = puedeElegir && !rolElegido

  const manageable = canManage(workspace?.myRole)

  return (
    <WorkspaceShell workspace={workspace} workspaceId={workspaceId}>
      {isPending ? (
        <Skeleton label="Cargando el equipo…" lines={3} />
      ) : (
        <CardGroup>
          {manageable && (
            <form
              className={styles.inviteForm}
              onSubmit={async (event) => {
                event.preventDefault()
                if (comprobando || faltaElegir) return
                // Invitar, no anadir: no entra en el equipo hasta que acepte. Entra como alumno o
                // como docente segun su tipo de cuenta, salvo que se pida que una cuenta docente
                // trabaje aqui como alumno; a responsable se le pasa despues, desde esta pantalla.
                await invitaciones.invite.mutateAsync({
                  email: email.trim(),
                  role: puedeElegir && (rolElegido === 'MIEMBRO' || rolElegido === 'DOCENTE') ? rolElegido : undefined,
                })
                setEmail('')
                setEleccion(null)
              }}
            >
              <Card
                title="Invitar a alguien"
                description="Entra en el equipo en cuanto acepte la invitación."
                aside={
                  <>
                    <input
                      id="invite"
                      type="email"
                      required
                      aria-label="Correo de la persona a invitar"
                      className={styles.control}
                      value={email}
                      onChange={(event) => setEmail(event.target.value)}
                      placeholder="companero@uni.es"
                    />
                    <Button
                      type="submit"
                      disabled={invitaciones.invite.isPending || !email.trim() || comprobando || faltaElegir}
                    >
                      <UserPlus size={14} strokeWidth={2.75} />
                      {invitaciones.invite.isPending ? 'Enviando…' : comprobando ? 'Comprobando…' : 'Invitar'}
                    </Button>
                  </>
                }
              >
                {puedeElegir && (
                  <fieldset className={styles.inviteChoice}>
                    <legend className={styles.inviteChoiceLegend}>
                      Esta cuenta es de docente. ¿Cómo entra en este espacio?
                      {faltaElegir && <span className={styles.inviteChoiceHint}> Elige una opción para poder invitar.</span>}
                    </legend>
                    {candidato?.allowedRoles.map((rol) => (
                      <label key={rol} className={styles.inviteOption}>
                        <input
                          type="radio"
                          name="invite-role"
                          checked={rolElegido === rol}
                          onChange={() => setEleccion({ correo, rol })}
                        />
                        <span>
                          <strong>{ROLE_LABELS[rol]}</strong>
                          {rol === 'DOCENTE' ? ' · solo sigue el progreso' : ' · trabaja aquí, y luego puedes hacerle responsable'}
                        </span>
                      </label>
                    ))}
                  </fieldset>
                )}
              </Card>
            </form>
          )}

          {members && members.length === 0 ? (
            <EmptyState
              icon={<Users size={22} strokeWidth={2.75} />}
              title="Todavía estás solo aquí"
              body="Invita a tus compañeros con su correo para repartiros el trabajo."
            />
          ) : (
            <Card
              title="Equipo"
              description={`${members?.length ?? 0} ${members?.length === 1 ? 'persona' : 'personas'} en este espacio de trabajo.`}
            >
              <ul className={styles.members}>
                {members?.map((member) => {
                  const isMe = member.user.id === user?.id
                  return (
                    <li key={member.id} className={styles.member}>
                      <div className={styles.memberRow}>
                        <Avatar user={member.user} avatarUrl={member.user.avatarUrl} size={34} />
                        <div className={styles.identity}>
                          <div className={styles.name}>
                            {member.user.fullName}
                            {isMe && <span className={styles.you}> (tu)</span>}
                          </div>
                          <div className={styles.mail}>{member.user.email}</div>
                        </div>

                        {manageable && member.role !== 'DOCENTE' ? (
                          <Dropdown
                            trigger={({ toggle }) => (
                              <button className={styles.roleButton} onClick={toggle}>
                                {ROLE_LABELS[member.role]}
                                <ChevronDown size={14} strokeWidth={2.75} aria-hidden="true" />
                              </button>
                            )}
                          >
                            {(close) => (
                              <>
                                {ROLES_DE_TRABAJO.map((item) => (
                                  <DropdownItem
                                    key={item}
                                    active={item === member.role}
                                    onClick={() => {
                                      close()
                                      mutations.updateRole.mutate({ userId: member.user.id, role: item })
                                    }}
                                  >
                                    {ROLE_LABELS[item]}
                                  </DropdownItem>
                                ))}
                              </>
                            )}
                          </Dropdown>
                        ) : (
                          <Tag variant={member.role === 'RESPONSABLE' ? 'accent' : 'neutral'}>
                            {ROLE_LABELS[member.role]}
                          </Tag>
                        )}

                        <span className={styles.joined}>{relativeTime(member.joinedAt)}</span>

                        {(manageable || isMe) && (
                          <ConfirmDialog
                            trigger={
                              <Button variant="ghost" small aria-label={isMe ? 'Salir del espacio de trabajo' : 'Quitar del espacio de trabajo'}>
                                <UserMinus size={14} strokeWidth={2.75} />
                              </Button>
                            }
                            title={isMe ? 'Salir del espacio de trabajo' : `Quitar a ${member.user.fullName}`}
                            description={
                              isMe
                                ? 'Dejarás de ver este espacio de trabajo y sus tareas.'
                                : 'Dejará de ver este espacio de trabajo y sus tareas.'
                            }
                            confirmLabel={isMe ? 'Salir' : 'Quitar'}
                            onConfirm={async () => {
                              await mutations.removeMember.mutateAsync(member.user.id)
                              if (isMe) navigate(ROUTES.workspaces)
                            }}
                          />
                        )}
                      </div>
                    </li>
                  )
                })}
              </ul>
              <InfoNote>
                <b>Responsable</b> administra el espacio de trabajo y genera el plan con IA. <b>Alumno</b> crea y mueve
                tareas. <b>Docente</b> solo sigue el progreso, y
                serlo se elige al invitar: después ya no se cambia. Quien recibe una invitación no entra en el
                equipo hasta que la acepta.
              </InfoNote>
            </Card>
          )}

          {pendientes && pendientes.length > 0 && (
            <Card
              title="Pendientes de aceptar"
              description="Todavía no son del equipo: no ven nada hasta que acepten."
            >
              <ul className={styles.members}>
                {pendientes.map((invitacion) => (
                  <li key={invitacion.id} className={styles.member}>
                    <div className={styles.memberRow}>
                      <Avatar user={invitacion.invited} avatarUrl={invitacion.invited.avatarUrl} size={34} />
                      <div className={styles.identity}>
                        <div className={styles.name}>{invitacion.invited.fullName}</div>
                        <div className={styles.mail}>{invitacion.invited.email}</div>
                      </div>

                      <Tag variant="neutral">{ROLE_LABELS[invitacion.role]}</Tag>
                      <span className={styles.joined}>
                        {invitacion.emailSent ? 'Invitación enviada' : 'Sin avisar por correo'}
                        {' · '}
                        {relativeTime(invitacion.createdAt)}
                      </span>

                      {manageable && (
                        <Button
                          variant="ghost"
                          small
                          aria-label={`Cancelar la invitación de ${invitacion.invited.fullName}`}
                          disabled={invitaciones.cancel.isPending}
                          onClick={() => invitaciones.cancel.mutate(invitacion.id)}
                        >
                          <X size={14} strokeWidth={2.75} />
                        </Button>
                      )}
                    </div>
                  </li>
                ))}
              </ul>
            </Card>
          )}
        </CardGroup>
      )}
    </WorkspaceShell>
  )
}
