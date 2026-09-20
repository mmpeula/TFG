import { useState } from 'react'
import { LogOut, MoreVertical, Trash2 } from 'lucide-react'
import { Dropdown, DropdownItem } from '@/components/ui/Controls'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import styles from './WorkspaceCardActions.module.css'
import { useMemberMutations, useWorkspaceMutations } from '@/hooks/useData'
import { useAuth } from '@/state/context'
import { canManage } from '@/lib/roles'
import type { WorkspaceResponse } from '@/types/api'

/**
 * Salir de un workspace o borrarlo, desde la propia lista.
 *
 * Estaba solo dentro de sus ajustes, que obliga a entrar en el workspace del que
 * justamente te quieres ir. Borrar es cosa del responsable; salirse lo puede
 * hacer cualquiera, pero el servidor lo impide si dejaria al equipo sin ningun
 * responsable, y ese aviso es el que se ve en pantalla.
 */
export function WorkspaceCardActions({ workspace }: { workspace: WorkspaceResponse }) {
  const { user } = useAuth()
  const [confirmando, setConfirmando] = useState<'salir' | 'borrar' | null>(null)

  const { remove } = useWorkspaceMutations(workspace.id)
  const { removeMember } = useMemberMutations(workspace.teamId)
  const responsable = canManage(workspace.myRole)

  return (
    <>
      <Dropdown
        trigger={({ toggle }) => (
          <button
            className={styles.trigger}
            onClick={toggle}
            aria-label={`Acciones de ${workspace.name}`}
          >
            <MoreVertical size={15} strokeWidth={2.75} />
          </button>
        )}
      >
        {(close) => (
          <>
            <DropdownItem
              onClick={() => {
                close()
                setConfirmando('salir')
              }}
            >
              <LogOut size={14} strokeWidth={2.75} />
              Salir del workspace
            </DropdownItem>
            {responsable && (
              <DropdownItem
                onClick={() => {
                  close()
                  setConfirmando('borrar')
                }}
              >
                <Trash2 size={14} strokeWidth={2.75} />
                Borrar
              </DropdownItem>
            )}
          </>
        )}
      </Dropdown>

      <ConfirmDialog
        open={confirmando === 'salir'}
        onOpenChange={(abierto) => setConfirmando(abierto ? 'salir' : null)}
        title={`Salir de «${workspace.name}»`}
        description="Dejaras de ver este workspace y sus tareas. Lo que ya hiciste se queda con el equipo."
        confirmLabel="Salir"
        isPending={removeMember.isPending}
        onConfirm={async () => {
          if (user) await removeMember.mutateAsync(user.id)
        }}
      />

      <ConfirmDialog
        open={confirmando === 'borrar'}
        onOpenChange={(abierto) => setConfirmando(abierto ? 'borrar' : null)}
        title={`Borrar «${workspace.name}»`}
        description="Se borraran su tablero, sus tareas, sus comentarios y sus ficheros, para todo el equipo. No se puede deshacer."
        isPending={remove.isPending}
        onConfirm={() => remove.mutateAsync()}
      />
    </>
  )
}
