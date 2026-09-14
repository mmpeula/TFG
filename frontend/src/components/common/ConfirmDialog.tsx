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
                await onConfirm()
                setOpen(false)
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
