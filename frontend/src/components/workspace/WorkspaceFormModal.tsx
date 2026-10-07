import { useState } from 'react'
import { DateInput } from '@/components/ui/DateInput'
import { Modal } from '@/components/ui/Modal'
import { DialogActions } from '@/components/ui/DialogActions'
import { Segmented } from '@/components/ui/Controls'
import { PhotoActions } from '@/components/ui/PhotoActions'
import { WorkspaceBadge } from './WorkspaceBadge'
import styles from './WorkspaceFormModal.module.css'
import { WORKSPACE_COLORS } from '@/lib/workspace'
import { DEFAULT_SPRINT_DAYS, SPRINT_DURATIONS } from '@/lib/sprints'
import { useWorkspaceMutations } from '@/hooks/useData'
import { useImagePicker } from '@/hooks/useImagePicker'
import type { WorkspacePayload } from '@/api/endpoints'
import type { WorkspaceResponse } from '@/types/api'

interface Props {
  open: boolean
  /** Si viene, el formulario edita en lugar de crear. */
  workspace?: WorkspaceResponse
  isPending?: boolean
  onClose: () => void
  onSubmit: (payload: WorkspacePayload) => Promise<WorkspaceResponse>
}

/**
 * Alta y edicion de un workspace.
 *
 * Nombre, descripcion, color e icono se eligen igual al crear que al editar: son
 * identidad del workspace, no hay motivo para posponerlos. Lo que si se pospone
 * son las fechas y la duracion del sprint, porque esas normalmente ya estan
 * escritas en el guion de la practica: se piden solo al editar, y al crear se
 * rellenan despues por dos caminos — se piden al generar el plan (ver
 * `GeneratePlanScreen`), o se ajustan aqui mismo, en modo edicion, desde Ajustes.
 */
export function WorkspaceFormModal({ open, workspace, isPending, onClose, onSubmit }: Props) {
  // El formulario solo existe mientras el dialogo esta abierto: asi se rellena al
  // montarse y no hace falta un efecto que lo reinicie en cada apertura.
  if (!open) return null
  return (
    <WorkspaceForm
      workspace={workspace}
      isPending={isPending}
      onClose={onClose}
      onSubmit={onSubmit}
    />
  )
}

function WorkspaceForm({ workspace, isPending, onClose, onSubmit }: Omit<Props, 'open'>) {
  const editing = Boolean(workspace)
  const mutations = useWorkspaceMutations(workspace?.id)
  const foto = useImagePicker()

  const [name, setName] = useState(workspace?.name ?? '')
  const [description, setDescription] = useState(workspace?.description ?? '')
  const [color, setColor] = useState<string>(workspace?.color ?? WORKSPACE_COLORS[0].id)
  const [icon, setIcon] = useState(workspace?.icon ?? '')
  const [startDate, setStartDate] = useState(workspace?.startDate ?? '')
  const [deadline, setDeadline] = useState(workspace?.deadline ?? '')
  const [sprintDays, setSprintDays] = useState(String(workspace?.sprintDays ?? DEFAULT_SPRINT_DAYS))

  const valido = name.trim().length > 0
  const guardandoFoto = mutations.uploadPhoto.isPending || mutations.removePhoto.isPending

  async function submit() {
    if (!valido || isPending || guardandoFoto) return
    try {
      // Al crear no se mandan fechas ni duracion de sprint: eso se propone desde el
      // guion (si lo dice) o se ajusta luego a mano, nunca se rellena aqui con un
      // valor inventado. El resto de la identidad del workspace si se manda ya.
      const saved = await onSubmit({
        name: name.trim(),
        description: description.trim() || undefined,
        color,
        icon: icon.trim() || undefined,
        ...(editing && {
          startDate: startDate || null,
          deadline: deadline || null,
          sprintDays: Number(sprintDays),
        }),
      })

      // La foto se sube aparte (multipart, no cabe en el mismo cuerpo JSON) y
      // hace falta un id: al editar ya existe, al crear lo acaba de devolver
      // onSubmit.
      if (foto.file) {
        await mutations.uploadPhoto.mutateAsync({ file: foto.file, targetId: saved.id })
        foto.saved()
      } else if (foto.removed && workspace) {
        await mutations.removePhoto.mutateAsync(workspace.id)
        foto.saved()
      }
      onClose()
    } catch {
      // El motivo ya lo cuenta el aviso de la mutacion; el formulario se queda abierto
      // con lo escrito para poder corregirlo.
    }
  }

  const coverActual = foto.removed ? null : workspace?.coverUrl ?? null
  const tieneFoto = Boolean(foto.preview || coverActual)

  return (
    <Modal
      open
      title={editing ? 'Editar el espacio de trabajo' : 'Nuevo espacio de trabajo'}
      onClose={onClose}
      footer={
        <DialogActions
          onCancel={onClose}
          onConfirm={submit}
          label={editing ? 'Guardar' : 'Crear espacio de trabajo'}
          pending={isPending || guardandoFoto}
          disabled={!valido}
        />
      }
    >
      <div className={styles.form}>
        <div className={styles.coverEditor}>
          {foto.preview
            ? <img className={styles.coverPreview} src={foto.preview} alt="Vista previa" />
            : (
              <WorkspaceBadge
                workspace={{ id: workspace?.id ?? 0, name: name || 'Espacio de trabajo', color, icon, coverUrl: coverActual }}
                className={styles.coverBadge}
              />
            )}
          <div className={styles.coverActions}>
            <PhotoActions picker={foto} hasPhoto={tieneFoto} />
          </div>
        </div>

        <label className={styles.label}>
          Nombre
          <input
            className={styles.input}
            value={name}
            autoFocus
            placeholder="Bases de Datos"
            onChange={(event) => setName(event.target.value)}
            onKeyDown={(event) => event.key === 'Enter' && submit()}
          />
        </label>

        <label className={styles.label}>
          Descripción <span className={styles.optional}>opcional</span>
          <input
            className={styles.input}
            value={description}
            placeholder="Práctica 2 - modelo E/R y consultas"
            onChange={(event) => setDescription(event.target.value)}
          />
        </label>

        {!tieneFoto && (
          <div className={styles.row}>
            <div className={styles.label}>
              Color
              <div className={styles.colors} role="radiogroup" aria-label="Color del espacio de trabajo">
                {WORKSPACE_COLORS.map((option) => (
                  <button
                    key={option.id}
                    role="radio"
                    aria-checked={color === option.id}
                    aria-label={option.label}
                    title={option.label}
                    className={[styles.color, color === option.id && styles.colorOn].filter(Boolean).join(' ')}
                    style={{ background: option.value }}
                    onClick={() => setColor(option.id)}
                  />
                ))}
              </div>
            </div>

            <label className={styles.label}>
              Icono <span className={styles.optional}>opcional</span>
              <input
                className={[styles.input, styles.iconInput].join(' ')}
                value={icon}
                maxLength={4}
                placeholder="BD"
                onChange={(event) => setIcon(event.target.value)}
              />
            </label>
          </div>
        )}

        {!editing && (
          <p className={styles.createHint}>
            Las fechas y la duración del sprint se proponen solas al subir el guion al
            generar el plan —si el documento las dice— o se ajustan luego a mano desde Ajustes.
          </p>
        )}

        {editing && (
          <>
            {/* Las dos fechas juntas: son las que parten el trabajo en sprints. */}
            <div className={styles.row}>
              <label className={styles.label}>
                Empieza <span className={styles.optional}>opcional</span>
                <DateInput
                  className={styles.input}
                  value={startDate}
                  onChange={setStartDate}
                />
              </label>

              <label className={styles.label}>
                Se entrega <span className={styles.optional}>opcional</span>
                <DateInput
                  className={styles.input}
                  value={deadline}
                  onChange={setDeadline}
                />
              </label>
            </div>

            <div className={styles.label}>
              Duración de cada sprint
              <Segmented
                options={SPRINT_DURATIONS}
                value={sprintDays}
                onChange={setSprintDays}
                ariaLabel="Duración del sprint"
              />
              <span className={styles.hint}>Vale para todo el plan; se puede cambiar más tarde en los ajustes.</span>
            </div>
          </>
        )}
      </div>
    </Modal>
  )
}
