import { colorOf, iconOf } from '@/lib/workspace'
import { useWorkspaceCoverImage } from '@/hooks/useData'
import type { WorkspaceResponse } from '@/types/api'

interface Props {
  workspace: Pick<WorkspaceResponse, 'id' | 'name' | 'color' | 'icon' | 'coverUrl'>
  /** Clase de cada pantalla (WorkspaceShell.module.css, WorkspaceCard.module.css...): fija tamano y radio. */
  className: string
}

/** El icono de color de siempre, o la foto del workspace si tiene una. */
export function WorkspaceBadge({ workspace, className }: Props) {
  const { data: photoUrl } = useWorkspaceCoverImage(workspace.coverUrl)

  if (photoUrl) {
    return <img className={className} src={photoUrl} alt="" style={{ objectFit: 'cover' }} />
  }

  return (
    <span className={className} style={{ background: colorOf(workspace) }} aria-hidden="true">
      {iconOf(workspace)}
    </span>
  )
}
