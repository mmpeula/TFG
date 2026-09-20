import { useState, type ReactNode } from 'react'
import { Modal } from '@/components/ui/Modal'
import { Button } from '@/components/ui/Button'

/** Confirmacion previa a una accion destructiva. Nunca se borra sin pasar por aqui. */
export function ConfirmDialog({
  trigger,
  title,
  description,
  confirmLabel = 'Borrar',
  onConfirm,
  isPending = false,
  open: controlledOpen,
  onOpenChange,
}: {
  trigger?: ReactNode
  title: string
  description: string
  confirmLabel?: string
  onConfirm: () => void | Promise<unknown>
  isPending?: boolean
  open?: boolean
  onOpenChange?: (open: boolean) => void
}) {
  const [uncontrolledOpen, setUncontrolledOpen] = useState(false)
  const open = controlledOpen ?? uncontrolledOpen
  const setOpen = onOpenChange ?? setUncontrolledOpen

  return (
    <>
      {trigger && <span onClick={() => setOpen(true)} role="presentation">{trigger}</span>}
      <Modal
        open={open}
        title={title}
        onClose={() => setOpen(false)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setOpen(false)}>Cancelar</Button>
            <Button
              variant="destructive"
              disabled={isPending}
              onClick={async () => {
                try {
                  await onConfirm()
                  setOpen(false)
                } catch {
                  // Que la accion se rechace es normal: el servidor tiene reglas que
                  // no se pueden comprobar desde aqui (salirse dejando al equipo sin
                  // responsable, por ejemplo). El motivo ya se le cuenta al usuario en
                  // un aviso; aqui solo hay que evitar que la promesa quede sin
                  // gestionar, y el dialogo se queda abierto para que pueda leerlo.
                }
              }}
            >
              {confirmLabel}
            </Button>
          </>
        }
      >
        {description}
      </Modal>
    </>
  )
}
