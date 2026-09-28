import { useEffect, useRef, useState } from 'react'
import { KeyRound, Pencil } from 'lucide-react'
import { HomeShell } from '@/components/shell/HomeShell'
import { Avatar } from '@/components/ui/Avatar'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { PasswordInput } from '@/components/ui/PasswordInput'
import { Tag } from '@/components/ui/Tag'
import { Section } from './Section'
import styles from './SettingsScreen.module.css'
import { useUserMutations } from '@/hooks/useData'
import { useAuth } from '@/state/context'
import { ROLE_LABELS } from '@/lib/roles'
import { formatDateTime } from '@/lib/format'
import type { UserResponse } from '@/types/api'

/**
 * Mi cuenta: quien eres, que rol global tienes, cuando se creo la cuenta y
 * como editarla.
 */
export function SettingsScreen() {
  const { user } = useAuth()
  const [editOpen, setEditOpen] = useState(false)
  const [passwordOpen, setPasswordOpen] = useState(false)

  return (
    <HomeShell>
      <div className={styles.layout}>
        <h1 className={styles.pageTitle}>Mi cuenta</h1>

        <Section
          title="Perfil"
          action={
            <div className={styles.headerActions}>
              <Button variant="secondary" small onClick={() => setPasswordOpen(true)}>
                <KeyRound size={13} strokeWidth={2.75} />
                Cambiar contraseña
              </Button>
              <Button variant="secondary" small onClick={() => setEditOpen(true)}>
                <Pencil size={13} strokeWidth={2.75} />
                Editar
              </Button>
            </div>
          }
        >
          <div className={styles.profile}>
            <Avatar user={user} avatarUrl={user?.avatarUrl} size={56} />
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
        </Section>
      </div>

      <EditProfileModal open={editOpen} user={user ?? null} onClose={() => setEditOpen(false)} />
      <ChangePasswordModal open={passwordOpen} user={user ?? null} onClose={() => setPasswordOpen(false)} />
    </HomeShell>
  )
}

/** Nombre y foto de perfil; la contraseña tiene su propio dialogo. */
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
  const [avatarFile, setAvatarFile] = useState<File | null>(null)
  const [avatarPreview, setAvatarPreview] = useState<string | null>(null)
  const [removeAvatar, setRemoveAvatar] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const mutations = useUserMutations(user.id)

  // La vista previa es un objeto local: hay que liberarlo si se cambia de
  // fichero o se cierra el dialogo, o si no se acumulan URLs sin usar.
  useEffect(() => () => { if (avatarPreview) URL.revokeObjectURL(avatarPreview) }, [avatarPreview])

  const nombreValido = fullName.trim().length > 0
  const guardando = mutations.update.isPending || mutations.uploadAvatar.isPending || mutations.removeAvatar.isPending

  function pickFile(file: File | undefined) {
    if (!file) return
    if (avatarPreview) URL.revokeObjectURL(avatarPreview)
    setAvatarFile(file)
    setAvatarPreview(URL.createObjectURL(file))
    setRemoveAvatar(false)
  }

  function quitarFoto() {
    if (avatarPreview) URL.revokeObjectURL(avatarPreview)
    setAvatarFile(null)
    setAvatarPreview(null)
    setRemoveAvatar(true)
  }

  async function submit() {
    if (!nombreValido || guardando) return
    try {
      // Se limpia el estado de la foto nada mas confirmarla el servidor: si
      // el nombre falla despues, un reintento no debe volver a subirla.
      if (avatarFile) {
        await mutations.uploadAvatar.mutateAsync(avatarFile)
        setAvatarFile(null)
      } else if (removeAvatar) {
        await mutations.removeAvatar.mutateAsync()
        setRemoveAvatar(false)
      }
      if (fullName.trim() !== user.fullName) {
        await mutations.update.mutateAsync({ fullName: fullName.trim() })
      }
      onClose()
    } catch {
      // El aviso ya sale por el toast de la mutacion; el dialogo se queda
      // abierto con lo escrito para poder corregirlo.
    }
  }

  const avatarActual = removeAvatar ? null : user.avatarUrl
  const tieneFoto = Boolean(avatarPreview || avatarActual)

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
          <Button onClick={submit} disabled={!nombreValido || guardando}>
            {guardando ? 'Guardando…' : 'Guardar'}
          </Button>
        </>
      }
    >
      <div className={styles.form}>
        <div className={styles.avatarEditor}>
          {avatarPreview
            ? <img className={styles.avatarPreview} src={avatarPreview} alt="Vista previa" />
            : <Avatar user={user} avatarUrl={avatarActual} size={64} />}
          <div className={styles.avatarActions}>
            <input
              ref={fileInputRef}
              type="file"
              accept="image/png,image/jpeg,image/gif,image/webp"
              hidden
              onChange={(event) => pickFile(event.target.files?.[0])}
            />
            <Button variant="secondary" small onClick={() => fileInputRef.current?.click()}>
              Cambiar foto
            </Button>
            {tieneFoto && (
              <Button variant="ghost" small onClick={quitarFoto}>
                Quitar foto
              </Button>
            )}
          </div>
        </div>

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
      </div>
    </Modal>
  )
}

/** Contraseña actual mas la nueva por duplicado, para no equivocarse al escribirla. */
function ChangePasswordModal({
  open,
  user,
  onClose,
}: {
  open: boolean
  user: UserResponse | null
  onClose: () => void
}) {
  if (!open || !user) return null
  return <ChangePasswordForm user={user} onClose={onClose} />
}

function ChangePasswordForm({ user, onClose }: { user: UserResponse; onClose: () => void }) {
  const [currentPassword, setCurrentPassword] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const mutations = useUserMutations(user.id)

  const actualValida = currentPassword.length > 0
  const passwordValida = password.length >= 8
  const confirmacionValida = password === confirmPassword
  const valido = actualValida && passwordValida && confirmacionValida

  async function submit() {
    if (!valido || mutations.changePassword.isPending) return
    try {
      await mutations.changePassword.mutateAsync({
        fullName: user.fullName,
        currentPassword,
        password,
      })
      onClose()
    } catch {
      // Equivocarse con la contraseña actual es un final normal, no un fallo: el
      // aviso ya sale por el toast de la mutacion y el dialogo se queda abierto
      // con lo escrito para poder corregirlo.
    }
  }

  return (
    <Modal
      open
      title="Cambiar contraseña"
      onClose={onClose}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={submit} disabled={!valido || mutations.changePassword.isPending}>
            {mutations.changePassword.isPending ? 'Guardando…' : 'Guardar'}
          </Button>
        </>
      }
    >
      <div className={styles.form}>
        <label className={styles.label}>
          Contraseña actual
          <PasswordInput
            autoComplete="current-password"
            className={styles.control}
            value={currentPassword}
            autoFocus
            placeholder="La que usas ahora"
            onChange={(event) => setCurrentPassword(event.target.value)}
          />
          <span className={styles.optional}>
            Se pide para que una sesion abierta y sin vigilancia no baste para cambiarla.
          </span>
        </label>

        <label className={styles.label}>
          Nueva contraseña
          <PasswordInput
            autoComplete="new-password"
            className={styles.control}
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
          {!passwordValida && password.length > 0 && (
            <span className={styles.error}>Al menos 8 caracteres.</span>
          )}
        </label>

        <label className={styles.label}>
          Repite la nueva contraseña
          <PasswordInput
            autoComplete="new-password"
            className={styles.control}
            value={confirmPassword}
            onChange={(event) => setConfirmPassword(event.target.value)}
            onKeyDown={(event) => event.key === 'Enter' && submit()}
          />
          {!confirmacionValida && confirmPassword.length > 0 && (
            <span className={styles.error}>Las dos contraseñas no coinciden.</span>
          )}
        </label>
      </div>
    </Modal>
  )
}
