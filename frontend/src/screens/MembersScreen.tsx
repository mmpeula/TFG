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
  useInvitationMutations, useMemberMutations, useTeamInvitations, useTeamMembers, useWorkspace,
} from '@/hooks/useData'
import { useAuth } from '@/state/context'
import { canManage, ROLE_LABELS } from '@/lib/roles'
import { relativeTime } from '@/lib/format'
import { ROUTES } from '@/app/routes'
import type { Role } from '@/types/api'

const ROLES: Role[] = ['RESPONSABLE', 'MIEMBRO', 'DOCENTE']

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
  const [role, setRole] = useState<Role>('MIEMBRO')

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
                // Invitar, no anadir: no entra en el equipo hasta que acepte.
                await invitaciones.invite.mutateAsync({ email: email.trim(), role })
                setEmail('')
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
              </div>

              <div>
                <span className={styles.label}>Puede</span>
                <Dropdown
                  align="left"
                  trigger={({ toggle }) => (
                    <button type="button" className={styles.roleButton} onClick={toggle}>
                      {ROLE_LABELS[role]}
                    </button>
                  )}
                >
                  {(close) => (
                    <>
                      {ROLES.map((item) => (
                        <DropdownItem
                          key={item}
                          active={item === role}
                          onClick={() => {
                            close()
                            setRole(item)
                          }}
                        >
                          {ROLE_LABELS[item]}
                        </DropdownItem>
                      ))}
                    </>
                  )}
                </Dropdown>
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
                    <Avatar user={member.user} size={34} />
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
                            {ROLES.map((item) => (
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
                          <Button variant="ghost" small aria-label={isMe ? 'Salir del workspace' : 'Quitar del workspace'}>
                            <UserMinus size={14} strokeWidth={2.75} />
                          </Button>
                        }
                        title={isMe ? 'Salir del workspace' : `Quitar a ${member.user.fullName}`}
                        description={
                          isMe
                            ? 'Dejaras de ver este workspace y sus tareas.'
                            : 'Dejara de ver este workspace y sus tareas.'
                        }
                        confirmLabel={isMe ? 'Salir' : 'Quitar'}
                        onConfirm={async () => {
                          await mutations.removeMember.mutateAsync(member.user.id)
                          if (isMe) navigate(ROUTES.workspaces)
                        }}
                      />
                    )}
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
                    <Avatar user={invitacion.invited} size={34} />
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
                  </li>
                ))}
              </ul>
            </section>
          )}

          <p className={styles.help}>
            <b>Responsable</b> administra el workspace y lanza el copiloto. <b>Miembro</b> crea y mueve
            tareas. <b>Docente</b> solo sigue el progreso. Quien recibe una invitacion no entra en el
            equipo hasta que la acepta.
          </p>
        </div>
      )}
    </WorkspaceShell>
  )
}
