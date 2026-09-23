import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Clock3, Sparkles, UserMinus, UserPlus, Users, X } from 'lucide-react'
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
import type { Role, TeamMemberResponse } from '@/types/api'

const ROLES: Role[] = ['RESPONSABLE', 'MIEMBRO', 'DOCENTE']

/** El mismo tope que aplica el servidor (TeamMember.MAX_PLAN_NOTES). */
const MAX_NOTAS = 600

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
                    <div className={styles.memberRow}>
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
                    </div>

                    <MemberNotes
                      member={member}
                      editable={manageable}
                      onSave={(planNotes) =>
                        mutations.updateNotes.mutateAsync({ userId: member.user.id, planNotes })
                      }
                    />
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
                    </div>
                  </li>
                ))}
              </ul>
            </section>
          )}

          <p className={styles.help}>
            <b>Responsable</b> administra el workspace y genera el plan con IA. <b>Alumno</b> crea y mueve
            tareas. <b>Docente</b> solo sigue el progreso. Quien recibe una invitacion no entra en el
            equipo hasta que la acepta.
          </p>
        </div>
      )}
    </WorkspaceShell>
  )
}

/**
 * Lo que hay que saber de esta persona para repartirle trabajo al generar el plan.
 *
 * Se queda guardado y vale para todas las ejecuciones siguientes. Es la diferencia
 * con el campo de la pantalla de Generar plan, que solo afecta a la generacion que se
 * lanza en ese momento; por eso aqui se dice explicitamente «se guarda» y alli
 * «solo para esta vez»: escritas parecido y en dos sitios distintos, es lo unico
 * que evita que se confundan.
 */
function MemberNotes({
  member,
  editable,
  onSave,
}: {
  member: TeamMemberResponse
  editable: boolean
  onSave: (planNotes: string) => Promise<unknown>
}) {
  const guardadas = member.planNotes ?? ''
  const [texto, setTexto] = useState(guardadas)
  const [guardando, setGuardando] = useState(false)
  const [abierto, setAbierto] = useState(false)

  if (!editable) {
    // Quien no administra el workspace ve la ficha pero no la toca. Si no hay
    // nada escrito no se ensena un hueco vacio que no puede rellenar.
    if (!guardadas) return null
    return (
      <div className={styles.notes}>
        <p className={styles.notesText} title={guardadas}>
          <Sparkles size={11} strokeWidth={2.75} />
          {guardadas}
        </p>
      </div>
    )
  }

  if (!abierto) {
    return (
      <div className={styles.notes}>
        <button
          type="button"
          className={styles.notesToggle}
          // Se relee lo guardado al abrir y no solo al montar: si la ficha ha
          // cambiado por otro lado, el textarea no arranca con lo viejo.
          onClick={() => { setTexto(guardadas); setAbierto(true) }}
        >
          <Sparkles size={11} strokeWidth={2.75} />
          {guardadas ? (
            <span className={styles.notesPreview}>{guardadas}</span>
          ) : (
            <span className={styles.notesEmpty}>Anadir indicaciones para el plan</span>
          )}
        </button>
      </div>
    )
  }

  const cambiado = texto.trim() !== guardadas.trim()

  async function guardar() {
    if (!cambiado || guardando) return
    setGuardando(true)
    try {
      await onSave(texto.trim())
      setAbierto(false)
    } finally {
      setGuardando(false)
    }
  }

  return (
    <div className={styles.notes}>
      <label className={styles.notesLabel} htmlFor={`notas-${member.id}`}>
        <Sparkles size={11} strokeWidth={2.75} />
        Indicaciones para el plan
      </label>
      <textarea
        id={`notas-${member.id}`}
        className={styles.notesArea}
        value={texto}
        autoFocus
        maxLength={MAX_NOTAS}
        placeholder={`En que se maneja ${member.user.fullName.split(' ')[0]}, que prefiere y que conviene no darle. Por ejemplo: «Se defiende con Java y Spring. Prefiere backend. Evitar darle diseno de interfaz si hay alternativa.»`}
        onChange={(event) => setTexto(event.target.value)}
      />
      <div className={styles.notesFoot}>
        <span className={styles.notesHint}>
          Se guarda y vale para los proximos repartos. Lo que solo valga para una vez, escribelo al
          generar el plan.
        </span>
        <span className={styles.notesCount}>
          {texto.length}/{MAX_NOTAS}
          <Button
            variant="ghost"
            small
            onClick={() => { setTexto(guardadas); setAbierto(false) }}
            disabled={guardando}
          >
            Cancelar
          </Button>
          <Button small onClick={guardar} disabled={!cambiado || guardando}>
            {guardando ? 'Guardando…' : 'Guardar'}
          </Button>
        </span>
      </div>
    </div>
  )
}
