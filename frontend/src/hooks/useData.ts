import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  adminWorkspaces, boards, comments, invitations, projects, tasks, teams, users, workspaces,
  type TaskPayload, type WorkspacePayload,
} from '@/api/endpoints'
import { toErrorMessage } from '@/lib/api-client'
import { useUi } from '@/state/context'
import type { BoardResponse, Role, TaskResponse } from '@/types/api'

export const keys = {
  workspaces: ['workspaces'] as const,
  workspace: (id: number) => ['workspaces', id] as const,
  members: (id: number) => ['teams', id, 'members'] as const,
  invitations: (id: number) => ['teams', id, 'invitations'] as const,
  myInvitations: ['invitations', 'mine'] as const,
  inviteCandidate: (teamId: number, email: string) => ['teams', teamId, 'invite-candidate', email] as const,
  project: (id: number) => ['projects', 'detalle', id] as const,
  activity: (id: number) => ['projects', 'detalle', id, 'activity'] as const,
  boards: (projectId: number) => ['projects', 'detalle', projectId, 'boards'] as const,
  board: (id: number) => ['boards', id] as const,
  comments: (taskId: number) => ['tasks', taskId, 'comments'] as const,
  adminUsers: ['admin', 'users'] as const,
  adminWorkspaces: ['admin', 'workspaces'] as const,
}

/** Si hay un id con el que preguntar: mientras no lo hay (cargando lo de antes), la consulta espera. */
const hasId = (id?: number) => typeof id === 'number' && Number.isFinite(id)

export const useWorkspaces = () => useQuery({ queryKey: keys.workspaces, queryFn: workspaces.list })

export const useWorkspace = (workspaceId?: number) =>
  useQuery({
    queryKey: keys.workspace(workspaceId!),
    queryFn: () => workspaces.get(workspaceId!),
    enabled: hasId(workspaceId),
  })

export const useTeamMembers = (teamId?: number) =>
  useQuery({ queryKey: keys.members(teamId!), queryFn: () => teams.members(teamId!), enabled: hasId(teamId) })

/** Quien esta invitado y todavia no ha contestado, para la pantalla de miembros. */
export const useTeamInvitations = (teamId?: number) =>
  useQuery({
    queryKey: keys.invitations(teamId!),
    queryFn: () => teams.invitations(teamId!),
    enabled: hasId(teamId),
  })

/**
 * Con que roles se puede invitar al correo que se esta escribiendo. Solo se consulta con un correo
 * completo; sin cuenta (404) simplemente no hay eleccion que ofrecer, y el error de verdad
 * lo da la invitacion al enviarla.
 */
export const useInviteCandidate = (teamId?: number, email?: string) =>
  useQuery({
    queryKey: keys.inviteCandidate(teamId!, email!),
    queryFn: () => teams.inviteCandidate(teamId!, email!),
    enabled: hasId(teamId) && !!email,
    retry: false,
    staleTime: 30_000,
  })

/** Las invitaciones que esperan respuesta del usuario que esta mirando. */
export const useMyInvitations = () =>
  useQuery({ queryKey: keys.myInvitations, queryFn: invitations.mine })

export const useProject = (projectId?: number) =>
  useQuery({ queryKey: keys.project(projectId!), queryFn: () => projects.get(projectId!), enabled: hasId(projectId) })

export const useActivity = (projectId?: number) =>
  useQuery({ queryKey: keys.activity(projectId!), queryFn: () => projects.activity(projectId!), enabled: hasId(projectId) })

export const useProjectBoards = (projectId?: number) =>
  useQuery({ queryKey: keys.boards(projectId!), queryFn: () => boards.byProject(projectId!), enabled: hasId(projectId) })

export const useBoard = (boardId?: number) =>
  useQuery({ queryKey: keys.board(boardId!), queryFn: () => boards.get(boardId!), enabled: hasId(boardId) })

export const useComments = (taskId?: number) =>
  useQuery({ queryKey: keys.comments(taskId!), queryFn: () => comments.byTask(taskId!), enabled: hasId(taskId) })

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
      onSuccess: (workspace) => { invalidate(); pushToast(`Espacio de trabajo «${workspace.name}» creado`) },
      onError,
    }),
    update: useMutation({
      mutationFn: (payload: WorkspacePayload) => workspaces.update(workspaceId!, payload),
      onSuccess: () => { invalidate(); pushToast('Espacio de trabajo actualizado') },
      onError,
    }),
    remove: useMutation({
      mutationFn: () => workspaces.remove(workspaceId!),
      onSuccess: () => { invalidate(); pushToast('Espacio de trabajo borrado') },
      onError,
    }),
    uploadPhoto: useMutation({
      // Admite un id explicito porque, al crear, el workspace no lo tiene
      // todavia cuando se monta este hook: solo existe tras el alta.
      mutationFn: ({ file, targetId }: { file: File; targetId?: number }) =>
        workspaces.uploadPhoto(targetId ?? workspaceId!, file),
      onSuccess: () => {
        invalidate()
        // La URL de la foto no cambia al sustituirla (siempre la misma ruta), asi
        // que sin esto el navegador seguiria mostrando la imagen cacheada de antes.
        queryClient.invalidateQueries({ queryKey: ['workspace-cover'] })
        pushToast('Foto del espacio de trabajo actualizada')
      },
      onError,
    }),
    removePhoto: useMutation({
      mutationFn: (targetId?: number) => workspaces.removePhoto(targetId ?? workspaceId!),
      onSuccess: () => {
        invalidate()
        queryClient.invalidateQueries({ queryKey: ['workspace-cover'] })
        pushToast('Foto del espacio de trabajo eliminada')
      },
      onError,
    }),
  }
}

/** Foto de un workspace: se descarga con axios (para que lleve el token) y se cachea como URL temporal. */
export function useWorkspaceCoverImage(coverUrl?: string | null) {
  return useQuery({
    queryKey: ['workspace-cover', coverUrl],
    queryFn: () => workspaces.photoBlob(coverUrl!),
    enabled: !!coverUrl,
    staleTime: Infinity,
    gcTime: Infinity,
  })
}

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
      mutationFn: (payload: { email: string; role?: 'MIEMBRO' | 'DOCENTE' }) => teams.invite(teamId!, payload),
      onSuccess: (invitation) => {
        if (teamId) queryClient.invalidateQueries({ queryKey: keys.invitations(teamId) })
        pushToast(invitation.emailSent
          ? `Invitación enviada a ${invitation.invited.email}`
          : `Invitación creada para ${invitation.invited.email}, pero el correo no ha podido salir`)
      },
      onError,
    }),
    cancel: useMutation({
      mutationFn: (invitationId: number) => teams.cancelInvitation(teamId!, invitationId),
      onSuccess: () => { refrescarEquipo(); pushToast('Invitación cancelada') },
      onError,
    }),
    accept: useMutation({
      mutationFn: (token: string) => invitations.accept(token),
      onSuccess: () => { refrescarEquipo(); refrescarMias(); pushToast('Ya estás en el equipo') },
      onError,
    }),
    reject: useMutation({
      mutationFn: (token: string) => invitations.reject(token),
      onSuccess: () => { refrescarMias(); pushToast('Invitación rechazada') },
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
      onSuccess: (_data, variables) => {
        invalidate()
        // La lista del equipo tambien cambia: el workspace pasa a verse suspendido.
        queryClient.invalidateQueries({ queryKey: keys.workspaces })
        pushToast(variables.enabled ? 'Espacio de trabajo reactivado' : 'Espacio de trabajo suspendido')
      },
      onError,
    }),
    remove: useMutation({
      mutationFn: (workspaceId: number) => adminWorkspaces.remove(workspaceId),
      onSuccess: () => { invalidate(); pushToast('Espacio de trabajo borrado') },
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
  // Quien sale deja sus tareas sin terminar (las suelta el servidor), y queda anotado en la
  // actividad: sin esto el tablero lo seguiria ensenando asignado hasta que caducara la cache.
  const invalidateWork = () => {
    invalidate()
    queryClient.invalidateQueries({ queryKey: ['boards'] })
    queryClient.invalidateQueries({ queryKey: ['tasks'] })
    queryClient.invalidateQueries({ queryKey: ['projects'] })
  }

  return {
    updateRole: useMutation({
      mutationFn: ({ userId, role }: { userId: number; role: Role }) => teams.updateMemberRole(teamId!, userId, role),
      onSuccess: () => { invalidate(); pushToast('Rol actualizado') },
      onError,
    }),
    /** Lo que se tendra en cuenta de esa persona en los proximos planes generados. */
    updateNotes: useMutation({
      mutationFn: ({ userId, planNotes }: { userId: number; planNotes: string }) =>
        teams.updateMemberNotes(teamId!, userId, planNotes),
      onSuccess: (member) => {
        invalidate()
        pushToast(member.planNotes
          ? `Indicaciones de ${member.user.fullName} guardadas`
          : `Indicaciones de ${member.user.fullName} borradas`)
      },
      onError,
    }),
    removeMember: useMutation({
      mutationFn: (userId: number) => teams.removeMember(teamId!, userId),
      onSuccess: () => { invalidateWork(); pushToast('Ya no está en el espacio de trabajo') },
      onError,
    }),
  }
}

/** Editar el propio perfil: nombre siempre; foto y contraseña, cada una con lo suyo. */
export function useUserMutations(userId?: number) {
  const queryClient = useQueryClient()
  const { pushToast } = useUi()
  const onError = useOnError()

  const onProfileChanged = () => {
    // "auth","me" lleva el token como tercer trozo de la key: se invalida por
    // prefijo para no tener que conocerlo aqui.
    queryClient.invalidateQueries({ queryKey: ['auth', 'me'] })
  }

  // La URL de la foto no cambia al sustituirla (siempre /users/{id}/avatar), asi
  // que sin esto el navegador seguiria mostrando la imagen cacheada de antes.
  const onAvatarChanged = () => {
    onProfileChanged()
    queryClient.invalidateQueries({ queryKey: ['avatar-image'] })
  }

  return {
    /** El nombre, y la contraseña si se manda: es la misma llamada, solo cambia el aviso. */
    update: useMutation({
      mutationFn: (payload: { fullName: string; currentPassword?: string; password?: string }) =>
        users.update(userId!, payload),
      onSuccess: (_user, payload) => {
        onProfileChanged()
        pushToast(payload.password ? 'Contraseña actualizada' : 'Perfil actualizado')
      },
      onError,
    }),
    uploadAvatar: useMutation({
      mutationFn: (file: File) => users.uploadAvatar(userId!, file),
      onSuccess: () => { onAvatarChanged(); pushToast('Foto de perfil actualizada') },
      onError,
    }),
    removeAvatar: useMutation({
      mutationFn: () => users.removeAvatar(userId!),
      onSuccess: () => { onAvatarChanged(); pushToast('Foto de perfil eliminada') },
      onError,
    }),
  }
}

/**
 * Foto de perfil de un usuario: se descarga con axios (para que lleve el token
 * de sesion) y se cachea como URL temporal, asi no se vuelve a pedir mientras
 * el avatar se repite en varios sitios de la misma pantalla.
 */
export function useAvatarImage(avatarUrl?: string | null) {
  return useQuery({
    queryKey: ['avatar-image', avatarUrl],
    queryFn: () => users.avatarBlob(avatarUrl!),
    enabled: !!avatarUrl,
    staleTime: Infinity,
    gcTime: Infinity,
  })
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

const TASK_UPDATE = ['task-update'] as const

export function useTaskMutations(boardId?: number, projectId?: number) {
  const queryClient = useQueryClient()
  const { pushToast } = useUi()
  const onError = useOnError()
  const invalidate = () => {
    if (boardId) queryClient.invalidateQueries({ queryKey: keys.board(boardId) })
    if (projectId) queryClient.invalidateQueries({ queryKey: keys.activity(projectId) })
    queryClient.invalidateQueries({ queryKey: ['tasks'] })
    // El progreso y las pendientes de la tarjeta del workspace salen de sus tareas.
    queryClient.invalidateQueries({ queryKey: keys.workspaces })
  }

  return {
    create: useMutation({
      mutationFn: ({ columnId, payload }: { columnId: number; payload: TaskPayload }) =>
        tasks.create(columnId, payload),
      onSuccess: () => { invalidate(); pushToast('Tarea creada') },
      onError,
    }),
    /**
     * Cada cambio manda la tarea entera, asi que dos seguidos (marcar a dos personas en el
     * selector, por ejemplo) no pueden partir de la misma foto de la tarea: el segundo
     * borraria al primero. Por eso el tablero ensena el cambio en el acto (`preview`), los
     * guardados van de uno en uno y solo se recarga al acabar el ultimo; si se recargara
     * tras cada uno, la respuesta del primero volveria a pintar la tarea sin el segundo.
     */
    update: useMutation({
      mutationKey: TASK_UPDATE,
      scope: { id: 'task-update' },
      mutationFn: ({ taskId, payload }: { taskId: number; payload: TaskPayload; preview?: TaskResponse }) =>
        tasks.update(taskId, payload),
      onMutate: async ({ preview }) => {
        if (!preview || !boardId) return
        await queryClient.cancelQueries({ queryKey: keys.board(boardId) })
        queryClient.setQueryData<BoardResponse>(keys.board(boardId), (board) => board && {
          ...board,
          columns: board.columns?.map((column) => ({
            ...column,
            tasks: column.tasks?.map((item) => (item.id === preview.id ? preview : item)),
          })),
        })
      },
      onSuccess: () => pushToast('Tarea actualizada'),
      // Si falla, la recarga del final devuelve la tarea a lo que de verdad hay guardado.
      onError,
      onSettled: () => {
        if (queryClient.isMutating({ mutationKey: TASK_UPDATE }) <= 1) invalidate()
      },
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

/**
 * Comentar y borrar comentarios. Refresca tambien el tablero (la tarjeta cuenta los
 * comentarios de su tarea) y la actividad del espacio, donde queda anotado.
 */
export function useCommentMutations(taskId: number, boardId?: number, projectId?: number) {
  const queryClient = useQueryClient()
  const { pushToast } = useUi()
  const onError = useOnError()
  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: keys.comments(taskId) })
    if (boardId) queryClient.invalidateQueries({ queryKey: keys.board(boardId) })
    if (projectId) queryClient.invalidateQueries({ queryKey: keys.activity(projectId) })
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

