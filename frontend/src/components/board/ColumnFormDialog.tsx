import { useState, type ReactNode } from 'react'
import { Modal } from '@/components/ui/Modal'
import { Button } from '@/components/ui/Button'
import styles from './ColumnFormDialog.module.css'
import type { ColumnResponse } from '@/types/api'

interface Props {
  trigger?: ReactNode
  column?: ColumnResponse
  onSubmit: (payload: { name: string; wipLimit?: number | null }) => Promise<unknown>
  isPending?: boolean
  open?: boolean
  onOpenChange?: (open: boolean) => void
}

/** Alta y edicion de columnas, incluido su limite de trabajo en curso. */
export function ColumnFormDialog({
  trigger, column, onSubmit, isPending, open: controlledOpen, onOpenChange,
}: Props) {
  const [uncontrolledOpen, setUncontrolledOpen] = useState(false)
  const open = controlledOpen ?? uncontrolledOpen
  const setOpen = onOpenChange ?? setUncontrolledOpen

  return (
    <>
      {trigger && <span onClick={() => setOpen(true)} role="presentation">{trigger}</span>}
      {open && (
        <ColumnForm
          key={column?.id ?? 'nueva'}
          column={column}
          isPending={isPending}
          onClose={() => setOpen(false)}
          onSubmit={onSubmit}
        />
      )}
    </>
  )
}

/**
 * El formulario nace ya con los valores de la columna: al remontarse con `key`
 * no hace falta sincronizar el estado dentro de un efecto.
 */
function ColumnForm({
  column, isPending, onClose, onSubmit,
}: {
  column?: ColumnResponse
  isPending?: boolean
  onClose: () => void
  onSubmit: (payload: { name: string; wipLimit?: number | null }) => Promise<unknown>
}) {
  const [name, setName] = useState(column?.name ?? '')
  const [wipLimit, setWipLimit] = useState(column?.wipLimit != null ? String(column.wipLimit) : '')

  return (
    <Modal
      open
      title={column ? 'Editar columna' : 'Nueva columna'}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancelar</Button>
          <Button
            disabled={!name.trim() || isPending}
            onClick={async () => {
              await onSubmit({ name: name.trim(), wipLimit: wipLimit ? Number(wipLimit) : null })
              onClose()
            }}
          >
            {column ? 'Guardar' : 'Crear columna'}
          </Button>
        </>
      }
    >
      <div className={styles.form}>
        <div>
          <label className={styles.label} htmlFor="col-name">Nombre</label>
          <input
            id="col-name"
            className={styles.control}
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder="En revision"
            autoFocus
          />
        </div>
        <div>
          <label className={styles.label} htmlFor="col-wip">Limite de trabajo en curso (opcional)</label>
          <input
            id="col-wip"
            type="number"
            min={1}
            className={styles.control}
            value={wipLimit}
            onChange={(event) => setWipLimit(event.target.value)}
            placeholder="Sin limite"
          />
          <p className={styles.hint}>
            Cuando la columna lo supere, PlanifIA abrira una senal de columna saturada.
          </p>
        </div>
      </div>
    </Modal>
  )
}
