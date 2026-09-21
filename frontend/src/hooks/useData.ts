import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  adminWorkspaces, boards, comments, attachments, invitations, projects, tasks, teams, users, workspaces,
  type TaskPayload, type WorkspacePayload,
} from '@/api/endpoints'
import { toErrorMessage } from '@/lib/api-client'
import { useUi } from '@/state/context'
import type { AttachmentResponse, Role } from '@/types/api'

export const keys = {
  workspaces: ['workspaces'] as const,
  workspace: (id: number) => ['workspaces', id] as const,
  members: (id: number) => ['teams', id, 'members'] as const,
  invitations: (id: number) => ['teams', id, 'invitations'] as const,
  myInvitations: ['invitations', 'mine'] as const,
  project: (id: number) => ['projects', 'detalle', id] as const,
  activity: (id: number) => ['projects', 'detalle', id, 'activity'] as const,
  boards: (projectId: number) => ['projects', 'detalle', projectId, 'boards'] as const,
  board: (id: number) => ['boards', id] as const,
  comments: (taskId: number) => ['tasks', taskId, 'comments'] as const,
  attachments: (taskId: number) => ['tasks', taskId, 'attachments'] as const,
  adminUsers: ['admin', 'users'] as const,
  adminWorkspaces: ['admin', 'workspaces'] as const,
}

const enabled = (id?: number) => typeof id === 'number' && Number.isFinite(id)

export const useWorkspaces = () => useQuery({ queryKey: keys.workspaces, queryFn: workspaces.list })

export const useWorkspace = (workspaceId?: number) =>
  useQuery({
    queryKey: keys.workspace(workspaceId!),
    queryFn: () => workspaces.get(workspaceId!),
    enabled: enabled(workspaceId),
  })

export const useTeamMembers = (teamId?: number) =>
  useQuery({ queryKey: keys.members(teamId!), queryFn: () => teams.members(teamId!), enabled: enabled(teamId) })

/** Quien esta invitado y todavia no ha contestado, para la pantalla de miembros. */
export const useTeamInvitations = (teamId?: number) =>
  useQuery({
    queryKey: keys.invitations(teamId!),
    queryFn: () => teams.invitations(teamId!),
    enabled: enabled(teamId),
  })

/** Las invitaciones que esperan respuesta del usuario que esta mirando. */
export const useMyInvitations = () =>
  useQuery({ queryKey: keys.myInvitations, queryFn: invitations.mine })

export const useProject = (projectId?: number) =>
  useQuery({ queryKey: keys.project(projectId!), queryFn: () => projects.get(projectId!), enabled: enabled(projectId) })

export const useActivity = (projectId?: number) =>
  useQuery({ queryKey: keys.activity(projectId!), queryFn: () => projects.activity(projectId!), enabled: enabled(projectId) })

export const useProjectBoards = (projectId?: number) =>
  useQuery({ queryKey: keys.boards(projectId!), queryFn: () => boards.byProject(projectId!), enabled: enabled(projectId) })

export const useBoard = (boardId?: number) =>
  useQuery({ queryKey: keys.board(boardId!), queryFn: () => boards.get(boardId!), enabled: enabled(boardId) })

export const useComments = (taskId?: number) =>
  useQuery({ queryKey: keys.comments(taskId!), queryFn: () => comments.byTask(taskId!), enabled: enabled(taskId) })

export const useAttachments = (taskId?: number) =>
  useQuery({ queryKey: keys.attachments(taskId!), queryFn: () => attachments.byTask(taskId!), enabled: enabled(taskId) })

/** Toda mutacion avisa con un toast y refresca lo que toca. */
function useOnError() {
  const { pushToast } = useUi()
  return (error: unknown) => pushToast(toErrorMessage(error))
}

/**
 * Crear, editar y borrar workspaces. Se invalida tambien el espacio de proyectos
 * porque por debajo el workspace es uno de ellos, con su tablero y su equipo.
 */
export function useWorkspaceMutations(workspaceId?: number) {
  const queryClient = useQueryClient()
  const { pushToast } = useUi()
  const onError = useOnError()
  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: keys.workspaces })
    queryClient.invalidateQueries({ queryKey: ['projects'] })
  }

  return {
    create: useMutation({
      mutationFn: (payload: WorkspacePayload) => workspaces.create(payload),
      onSuccess: (workspace) => { invalidate(); pushToast(`Workspace «${workspace.name}» creado`) },
      onError,
    }),
    update: useMutation({
      mutationFn: (payload: WorkspacePayload) => workspaces.update(workspaceId!, payload),
      onSuccess: () => { invalidate(); pushToast('Workspace actualizado') },
      onError,
    }),
    remove: useMutation({
      mutationFn: () => workspaces.remove(workspaceId!),
      onSuccess: () => { invalidate(); pushToast('Workspace borrado') },
      onError,
    }),
  }
}

/**
 * Miembros del workspace. Sigue siendo el equipo por debajo, de ahi el nombre de
 * las llamadas; lo que cambia es que ya no hay pantalla de equipos donde crearlos
 * o borrarlos: eso lo hace el workspace.
 */
/**
 * Invitar, cancelar y responder.
 *
 * Invitar refresca las invitaciones pero no los miembros: por definicion no
 * entra nadie hasta que acepta. Responder si refresca las dos cosas, y ademas
 * los workspaces, porque al aceptar aparece uno nuevo en la lista.
 */
export function useInvitationMutations(teamId?: number) {
  const queryClient = useQueryClient()
  const { pushToast } = useUi()
  const onError = useOnError()

  const refrescarEquipo = () => {
    if (teamId) {
      queryClient.invalidateQueries({ queryKey: keys.invitations(teamId) })
      queryClient.invalidateQueries({ queryKey: keys.members(teamId) })
    }
  }
  const refrescarMias = () => {
    queryClient.invalidateQueries({ queryKey: keys.myInvitations })
    queryClient.invalidateQueries({ queryKey: keys.workspaces })
  }

  return {
    invite: useMutation({
      mutationFn: (payload: { email: string; role: Role }) => teams.invite(teamId!, payload),
      onSuccess: (invitation) => {
        if (teamId) queryClient.invalidateQueries({ queryKey: keys.invitations(teamId) })
        pushToast(invitation.emailSent
          ? `Invitacion enviada a ${invitation.invited.email}`
          : `Invitacion creada para ${invitation.invited.email}, pero el correo no ha podido salir`)
      },
      onError,
    }),
    cancel: useMutation({
      mutationFn: (invitationId: number) => teams.cancelInvitation(teamId!, invitationId),
      onSuccess: () => { refrescarEquipo(); pushToast('Invitacion cancelada') },
      onError,
    }),
    accept: useMutation({
      mutationFn: (token: string) => invitations.accept(token),
      onSuccess: () => { refrescarEquipo(); refrescarMias(); pushToast('Ya estas en el equipo') },
      onError,
    }),
    reject: useMutation({
      mutationFn: (token: string) => invitations.reject(token),
      onSuccess: () => { refrescarMias(); pushToast('Invitacion rechazada') },
      onError,
    }),
  }
}

/** Todos los workspaces de la plataforma, para el panel de soporte. */
export const useAdminWorkspaces = () =>
  useQuery({ queryKey: keys.adminWorkspaces, queryFn: adminWorkspaces.list })

/**
 * Las acciones de soporte sobre un workspace ajeno. Existen para desatascar o
 * parar, no para participar: ni escriben en el tablero ni meten a nadie nuevo
 * en el equipo.
 */
export function useAdminWorkspaceMutations() {
  const queryClient = useQueryClient()
  const { pushToast } = useUi()
  const onError = useOnError()
  const invalidate = () => queryClient.invalidateQueries({ queryKey: keys.adminWorkspaces })

  return {
    cambiarRol: useMutation({
      mutationFn: ({ workspaceId, userId, role }: { workspaceId: number; userId: number; role: Role }) =>
        adminWorkspaces.cambiarRol(workspaceId, userId, role),
      onSuccess: () => { invalidate(); pushToast('Rol actualizado') },
      onError,
    }),
    setEnabled: useMutation({
      mutationFn: ({ workspaceId, enabled }: { workspaceId: number; enabled: boolean }) =>
        adminWorkspaces.setEnabled(workspaceId, enabled),
      onSuccess: (_data, { enabled }) => {
        invalidate()
        // La lista del equipo tambien cambia: el workspace pasa a verse suspendido.
        queryClient.invalidateQueries({ queryKey: keys.workspaces })
        pushToast(enabled ? 'Workspace reactivado' : 'Workspace suspendido')
      },
      onError,
    }),
    remove: useMutation({
      mutationFn: (workspaceId: number) => adminWorkspaces.remove(workspaceId),
      onSuccess: () => { invalidate(); pushToast('Workspace borrado') },
      onError,
    }),
  }
}

export function useMemberMutations(teamId?: number) {
  const queryClient = useQueryClient()
  const { pushToast } = useUi()
  const onError = useOnError()
  const invalidate = () => {
    if (teamId) queryClient.invalidateQueries({ queryKey: keys.members(teamId) })
    // La tarjeta del workspace muestra cuanta gente hay.
    queryClient.invalidateQueries({ queryKey: keys.workspaces })
  }

  return {
    addMember: useMutation({
      mutationFn: (payload: { email: string; role: Role }) => teams.addMember(teamId!, payload),
      onSuccess: (member) => { invalidate(); pushToast(`${member.user.fullName} anadido al workspace`) },
      onError,
    }),
    updateRole: useMutation({
      mutationFn: ({ userId, role }: { userId: number; role: Role }) => teams.updateMemberRole(teamId!, userId, role),
      onSuccess: () => { invalidate(); pushToast('Rol actualizado') },
      onError,
    }),
    removeMember: useMutation({
      mutationFn: (userId: number) => teams.removeMember(teamId!, userId),
      onSuccess: () => { invalidate(); pushToast('Ya no esta en el workspace') },
      onError,
    }),
  }
}

/** Editar el propio perfil: nombre siempre, contrasena solo si se rellena. */
export function useUserMutations(userId?: number) {
  const queryClient = useQueryClient()
  const { pushToast } = useUi()
  const onError = useOnError()

  return {
    update: useMutation({
      mutationFn: (payload: { fullName: string; password?: string }) => users.update(userId!, payload),
      onSuccess: () => {
        // "auth","me" lleva el token como tercer trozo de la key: se invalida por
        // prefijo para no tener que conocerlo aqui.
        queryClient.invalidateQueries({ queryKey: ['auth', 'me'] })
        pushToast('Perfil actualizado')
      },
      onError,
    }),
  }
}

/**
 * Listado de la plataforma para el panel de administracion (rol ADMIN).
 *
 * Trae hasta 200 cuentas de una vez y el filtrado por texto se hace en el
 * cliente (ver AdminScreen), igual que se hace con las tareas del tablero:
 * a esta escala una unica peticion es mas sencilla que repetirla en cada tecla.
 */
export function useAdminUsers() {
  return useQuery({ queryKey: keys.adminUsers, queryFn: () => users.list('') })
}

/** Cambiar el rol global, dar de baja o reactivar una cuenta. Solo para ADMIN. */
export function useAdminUserMutations() {
  const queryClient = useQueryClient()
  const { pushToast } = useUi()
  const onError = useOnError()
  const invalidate = () => queryClient.invalidateQueries({ queryKey: keys.adminUsers })

  return {
    updateRole: useMutation({
      mutationFn: ({ userId, role }: { userId: number; role: Role }) => users.updateGlobalRole(userId, role),
      onSuccess: () => { invalidate(); pushToast('Rol actualizado') },
      onError,
    }),
    disable: useMutation({
      mutationFn: (userId: number) => users.disable(userId),
      onSuccess: () => { invalidate(); pushToast('Cuenta deshabilitada') },
      onError,
    }),
    enable: useMutation({
      mutationFn: (userId: number) => users.enable(userId),
      onSuccess: () => { invalidate(); pushToast('Cuenta reactivada') },
      onError,
    }),
  }
}

export function useBoardMutations(boardId?: number, projectId?: number) {
  const queryClient = useQueryClient()
  const { pushToast } = useUi()
  const onError = useOnError()
  const invalidate = () => {
    if (boardId) queryClient.invalidateQueries({ queryKey: keys.board(boardId) })
    if (projectId) {
      queryClient.invalidateQueries({ queryKey: keys.boards(projectId) })
      queryClient.invalidateQueries({ queryKey: keys.activity(projectId) })
    }
    queryClient.invalidateQueries({ queryKey: ['tasks'] })
  }

  return {
    createBoard: useMutation({
      mutationFn: (payload: { name: string; description?: string }) => boards.create(projectId!, payload),
      onSuccess: () => { invalidate(); pushToast('Tablero creado con sus columnas por defecto') },
      onError,
    }),
    removeBoard: useMutation({
      mutationFn: (id: number) => boards.remove(id),
      onSuccess: () => { invalidate(); pushToast('Tablero borrado') },
      onError,
    }),
    createColumn: useMutation({
      mutationFn: (payload: { name: string; wipLimit?: number | null }) => boards.createColumn(boardId!, payload),
      onSuccess: () => { invalidate(); pushToast('Columna creada') },
      onError,
    }),
    updateColumn: useMutation({
      mutationFn: ({ columnId, payload }: { columnId: number; payload: { name: string; wipLimit?: number | null } }) =>
        boards.updateColumn(columnId, payload),
      onSuccess: () => { invalidate(); pushToast('Columna actualizada') },
      onError,
    }),
    moveColumn: useMutation({
      mutationFn: ({ columnId, position }: { columnId: number; position: number }) =>
        boards.moveColumn(columnId, position),
      onSuccess: invalidate,
      onError: (error) => { onError(error); invalidate() },
    }),
    removeColumn: useMutation({
      mutationFn: (columnId: number) => boards.removeColumn(columnId),
      onSuccess: () => { invalidate(); pushToast('Columna borrada') },
      onError,
    }),
  }
}

export function useTaskMutations(boardId?: number, projectId?: number) {
  const queryClient = useQueryClient()
  const { pushToast } = useUi()
  const onError = useOnError()
  const invalidate = () => {
    if (boardId) queryClient.invalidateQueries({ queryKey: keys.board(boardId) })
    if (projectId) queryClient.invalidateQueries({ queryKey: keys.activity(projectId) })
    queryClient.invalidateQueries({ queryKey: ['tasks'] })
  }

  return {
    create: useMutation({
      mutationFn: ({ columnId, payload }: { columnId: number; payload: TaskPayload }) =>
        tasks.create(columnId, payload),
      onSuccess: () => { invalidate(); pushToast('Tarea creada') },
      onError,
    }),
    update: useMutation({
      mutationFn: ({ taskId, payload }: { taskId: number; payload: TaskPayload }) => tasks.update(taskId, payload),
      onSuccess: () => { invalidate(); pushToast('Tarea actualizada') },
      onError,
    }),
    /** Persiste el drag & drop. Si el backend lo rechaza, se recarga el tablero. */
    move: useMutation({
      mutationFn: ({ taskId, columnId, position }: { taskId: number; columnId: number; position: number }) =>
        tasks.move(taskId, { columnId, position }),
      onSuccess: invalidate,
      onError: (error) => { onError(error); invalidate() },
    }),
    remove: useMutation({
      mutationFn: (taskId: number) => tasks.remove(taskId),
      onSuccess: () => { invalidate(); pushToast('Tarea borrada') },
      onError,
    }),
  }
}

export function useCommentMutations(taskId: number, onChanged?: () => void) {
  const queryClient = useQueryClient()
  const { pushToast } = useUi()
  const onError = useOnError()
  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: keys.comments(taskId) })
    onChanged?.()
  }

  return {
    create: useMutation({ mutationFn: (content: string) => comments.create(taskId, content), onSuccess: invalidate, onError }),
    update: useMutation({
      mutationFn: ({ commentId, content }: { commentId: number; content: string }) => comments.update(commentId, content),
      onSuccess: invalidate,
      onError,
    }),
    remove: useMutation({
      mutationFn: (commentId: number) => comments.remove(commentId),
      onSuccess: () => { invalidate(); pushToast('Comentario borrado') },
      onError,
    }),
  }
}

export function useAttachmentMutations(taskId: number, onChanged?: () => void) {
  const queryClient = useQueryClient()
  const { pushToast } = useUi()
  const onError = useOnError()
  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: keys.attachments(taskId) })
    onChanged?.()
  }

  return {
    upload: useMutation({
      mutationFn: (file: File) => attachments.upload(taskId, file),
      onSuccess: () => { invalidate(); pushToast('Fichero adjuntado') },
      onError,
    }),
    remove: useMutation({
      mutationFn: (attachmentId: number) => attachments.remove(attachmentId),
      onSuccess: () => { invalidate(); pushToast('Adjunto borrado') },
      onError,
    }),
    download: useMutation({ mutationFn: (item: AttachmentResponse) => attachments.download(item), onError }),
  }
}
