import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Clock3, UserMinus, UserPlus, Users, X } from 'lucide-react'
import { WorkspaceShell } from '@/components/shell/WorkspaceShell'
import { Avatar } from '@/components/ui/Avatar'
import { Button } from '@/components/ui/Button'
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
 * A que puede cambiarse el rol de alguien ya en el equipo. Quien esta como docente
 * es por fuerza una cuenta docente, y una cuenta docente puede trabajar en el equipo
 * (alumno) o dirigirlo (responsable) igual que un alumno; por eso se le ofrecen los
 * tres. A quien ya trabaja no se le ofrece docente: la pantalla no sabe si su cuenta
 * lo es, y el servidor lo rechazaria si no.
 */
function rolesDisponiblesPara(actual: Role): Role[] {
  return actual === 'DOCENTE' ? ['DOCENTE', 'MIEMBRO', 'RESPONSABLE'] : ['MIEMBRO', 'RESPONSABLE']
}

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
  const { data: candidato } = useInviteCandidate(workspace?.teamId, correoValido ? correo : undefined)
  // La eleccion (docente o alumno) solo existe si la cuenta admite las dos, es decir, si es de docente.
  const puedeElegir = correoValido && (candidato?.allowedRoles.length ?? 0) > 1
  const [eleccion, setEleccion] = useState<{ correo: string; rol: Role } | null>(null)
  // Cada correo parte de su rol por defecto: una eleccion hecha para otro correo no se arrastra.
  const rolElegido = eleccion?.correo === correo ? eleccion.rol : candidato?.defaultRole ?? undefined

  const manageable = canManage(workspace?.myRole)

  return (
    <WorkspaceShell workspace={workspace} workspaceId={workspaceId}>
      {isPending ? (
        <Skeleton label="Cargando el equipo…" lines={3} />
      ) : (
        <div className={styles.layout}>
          {manageable && (
            <form
              className={styles.invite}
              onSubmit={async (event) => {
                event.preventDefault()
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
              <div className={styles.inviteField}>
                <label className={styles.label} htmlFor="invite">
                  Invitar a alguien
                </label>
                <input
                  id="invite"
                  type="email"
                  required
                  className={styles.control}
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  placeholder="companero@uni.es"
                />
                {puedeElegir && (
                  <fieldset className={styles.inviteChoice}>
                    <legend className={styles.inviteChoiceLegend}>
                      Esta cuenta es de docente. ¿Cómo entra en este espacio?
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
              </div>

              <Button type="submit" disabled={invitaciones.invite.isPending || !email.trim()}>
                <UserPlus size={14} strokeWidth={2.75} />
                {invitaciones.invite.isPending ? 'Enviando…' : 'Invitar'}
              </Button>
            </form>
          )}

          {members && members.length === 0 ? (
            <EmptyState
              icon={<Users size={22} strokeWidth={2.75} />}
              title="Todavia estas solo aqui"
              body="Invita a tus companeros con su correo para repartiros el trabajo."
            />
          ) : (
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

                      {manageable ? (
                        <Dropdown
                          trigger={({ toggle }) => (
                            <button className={styles.roleButton} onClick={toggle}>
                              {ROLE_LABELS[member.role]}
                            </button>
                          )}
                        >
                          {(close) => (
                            <>
                              {rolesDisponiblesPara(member.role).map((item) => (
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
                              ? 'Dejaras de ver este espacio de trabajo y sus tareas.'
                              : 'Dejara de ver este espacio de trabajo y sus tareas.'
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
          )}

          {pendientes && pendientes.length > 0 && (
            <section className={styles.pending}>
              <h2 className={styles.pendingTitle}>
                <Clock3 size={13} strokeWidth={2.75} />
                Pendientes de aceptar
              </h2>
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
                        {invitacion.emailSent ? 'Invitacion enviada' : 'Sin avisar por correo'}
                        {' · '}
                        {relativeTime(invitacion.createdAt)}
                      </span>

                      {manageable && (
                        <Button
                          variant="ghost"
                          small
                          aria-label={`Cancelar la invitacion de ${invitacion.invited.fullName}`}
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
            </section>
          )}

          <p className={styles.help}>
            <b>Responsable</b> administra el espacio de trabajo y genera el plan con IA. <b>Alumno</b> crea y mueve
            tareas. <b>Docente</b> solo sigue el progreso. Quien recibe una invitacion no entra en el
            equipo hasta que la acepta.
          </p>
        </div>
      )}
    </WorkspaceShell>
  )
}
