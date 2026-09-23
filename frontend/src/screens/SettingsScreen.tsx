import { useState } from 'react'
import { Pencil } from 'lucide-react'
import { HomeShell } from '@/components/shell/HomeShell'
import { Avatar } from '@/components/ui/Avatar'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { Tag } from '@/components/ui/Tag'
import { Section } from './Section'
import styles from './SettingsScreen.module.css'
import { useUserMutations } from '@/hooks/useData'
import { useAuth } from '@/state/context'
import { ROLE_DESCRIPTIONS, ROLE_LABELS } from '@/lib/roles'
import { formatDateTime } from '@/lib/format'
import type { UserResponse } from '@/types/api'

/**
 * Mi cuenta: quien eres, qué rol global tienes y cómo cerrar sesión. El rol
 * que de verdad manda en cada workspace es el del equipo, no este — por eso
 * se explica junto a la sesión y no se deja como si fuera lo único que cuenta.
 */
export function SettingsScreen() {
  const { user, signOut } = useAuth()
  const [editOpen, setEditOpen] = useState(false)

  return (
    <HomeShell>
      <div className={styles.layout}>
        <h1 className={styles.pageTitle}>Mi cuenta</h1>

        <Section
          title="Perfil"
          action={
            <Button variant="secondary" small onClick={() => setEditOpen(true)}>
              <Pencil size={13} strokeWidth={2.75} />
              Editar
            </Button>
          }
        >
          <div className={styles.profile}>
            <Avatar user={user} size={56} />
            <div className={styles.identity}>
              <div className={styles.name}>{user?.fullName}</div>
              <div className={styles.hint}>{user?.email}</div>
            </div>
          </div>

          <dl className={styles.data}>
            <div>
              <dt>Rol global</dt>
              <dd>{user && <Tag variant="outline">{ROLE_LABELS[user.role]}</Tag>}</dd>
            </div>
            <div>
              <dt>Cuenta creada</dt>
              <dd>{formatDateTime(user?.createdAt)}</dd>
            </div>
          </dl>

          <p className={styles.note}>
            {user ? ROLE_DESCRIPTIONS[user.role] : ''} — pero el rol que manda en cada workspace es
            el que te haya dado su responsable, no este.
          </p>
        </Section>

        <Section title="Sesion">
          <div className={styles.sessionRow}>
            <p className={styles.hint}>Cierra la sesion en este dispositivo.</p>
            <Button variant="secondary" small onClick={signOut}>
              Cerrar sesion
            </Button>
          </div>
        </Section>

        <Section title="Sobre PlanifIA" subtitle="version de desarrollo">
          <p className={styles.hint}>
            Herramienta de gestion colaborativa de proyectos universitarios: tableros Kanban,
            senales que se resuelven y un modulo de planificacion con IA todavia en construccion.
          </p>
        </Section>
      </div>

      <EditProfileModal open={editOpen} user={user ?? null} onClose={() => setEditOpen(false)} />
    </HomeShell>
  )
}

/** Nombre siempre; la contrasena solo se cambia si se escribe algo en su campo. */
function EditProfileModal({
  open,
  user,
  onClose,
}: {
  open: boolean
  user: UserResponse | null
  onClose: () => void
}) {
  // Solo existe mientras el dialogo esta abierto: asi arranca con los datos de
  // ahora mismo y no hace falta un efecto que lo reinicie en cada apertura.
  if (!open || !user) return null
  return <EditProfileForm user={user} onClose={onClose} />
}

function EditProfileForm({ user, onClose }: { user: UserResponse; onClose: () => void }) {
  const [fullName, setFullName] = useState(user.fullName)
  const [currentPassword, setCurrentPassword] = useState('')
  const [password, setPassword] = useState('')
  const mutations = useUserMutations(user.id)

  // Solo se piden las contrasenas si se esta cambiando la contrasena; quien entra
  // aqui a corregirse una tilde del nombre no tiene por que escribir nada mas.
  const cambiaPassword = password.length > 0

  const nombreValido = fullName.trim().length > 0
  const passwordValida = !cambiaPassword || password.length >= 8
  const actualValida = !cambiaPassword || currentPassword.length > 0
  const valido = nombreValido && passwordValida && actualValida

  async function submit() {
    if (!valido || mutations.update.isPending) return
    try {
      await mutations.update.mutateAsync({
        fullName: fullName.trim(),
        currentPassword: cambiaPassword ? currentPassword : undefined,
        password: cambiaPassword ? password : undefined,
      })
      onClose()
    } catch {
      // Equivocarse con la contrasena actual es un final normal, no un fallo: el
      // aviso ya sale por el toast de la mutacion y el dialogo se queda abierto
      // con lo escrito para poder corregirlo.
    }
  }

  return (
    <Modal
      open
      title="Editar perfil"
      onClose={onClose}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={submit} disabled={!valido || mutations.update.isPending}>
            {mutations.update.isPending ? 'Guardando…' : 'Guardar'}
          </Button>
        </>
      }
    >
      <div className={styles.form}>
        <label className={styles.label}>
          Nombre
          <input
            className={styles.control}
            value={fullName}
            autoFocus
            onChange={(event) => setFullName(event.target.value)}
            onKeyDown={(event) => event.key === 'Enter' && submit()}
          />
        </label>

        <label className={styles.label}>
          Nueva contrasena <span className={styles.optional}>opcional</span>
          <input
            type="password"
            autoComplete="new-password"
            className={styles.control}
            value={password}
            placeholder="Dejar en blanco para no cambiarla"
            onChange={(event) => setPassword(event.target.value)}
          />
          {!passwordValida && <span className={styles.error}>Al menos 8 caracteres.</span>}
        </label>

        {cambiaPassword && (
          <label className={styles.label}>
            Contrasena actual
            <input
              type="password"
              autoComplete="current-password"
              className={styles.control}
              value={currentPassword}
              placeholder="La que usas ahora"
              onChange={(event) => setCurrentPassword(event.target.value)}
            />
            <span className={styles.optional}>
              Se pide para que una sesion abierta y sin vigilancia no baste para cambiarla.
            </span>
          </label>
        )}
      </div>
    </Modal>
  )
}
