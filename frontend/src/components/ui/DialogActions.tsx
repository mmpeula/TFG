import { Button } from '@/components/ui/Button'

/**
 * El pie de un dialogo con formulario: salir sin hacer nada y la accion principal, que
 * mientras se guarda dice que lo esta haciendo y no se puede volver a pulsar.
 */
export function DialogActions({
  onCancel,
  onConfirm,
  label,
  pendingLabel = 'Guardando…',
  cancelLabel = 'Cancelar',
  pending = false,
  disabled = false,
}: {
  onCancel: () => void
  onConfirm: () => void
  label: string
  pendingLabel?: string
  cancelLabel?: string
  pending?: boolean
  disabled?: boolean
}) {
  return (
    <>
      <Button variant="ghost" onClick={onCancel}>
        {cancelLabel}
      </Button>
      <Button onClick={onConfirm} disabled={disabled || pending}>
        {pending ? pendingLabel : label}
      </Button>
    </>
  )
}
