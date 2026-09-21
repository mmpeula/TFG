import { useMemo, useState } from 'react'
import { Search, UserCheck, UserX } from 'lucide-react'
import { Avatar } from '@/components/ui/Avatar'
import { Button } from '@/components/ui/Button'
import { Tag } from '@/components/ui/Tag'
import { Chip, Dropdown, DropdownItem } from '@/components/ui/Controls'
import { EmptyState } from '@/components/ui/EmptyState'
import { Skeleton } from '@/components/ui/Skeleton'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import styles from './AdminPanel.module.css'
import { useAdminUserMutations, useAdminUsers } from '@/hooks/useData'
import { useAuth } from '@/state/context'
import { ROLE_LABELS } from '@/lib/roles'
import type { Role, UserResponse } from '@/types/api'

/** Los tres roles de cuenta. RESPONSABLE no esta: se es de un equipo, no de la plataforma. */
const ROLES: Role[] = ['MIEMBRO', 'DOCENTE', 'ADMIN']

type Filtro = Role | 'TODAS'

function matches(user: UserResponse, term: string): boolean {
  const q = term.trim().toLowerCase()
  if (!q) return true
  return user.fullName.toLowerCase().includes(q) || user.email.toLowerCase().includes(q)
}

/**
 * Cuentas de toda la plataforma: rol global y alta o baja.
 *
 * La lista es larga, asi que lo primero es poder recortarla: buscador, filtro
 * por rol y un recuento que dice de un vistazo cuantas hay de cada tipo. Las
 * filas van en una rejilla con columnas fijas para que nombres, correos y roles
 * queden alineados; con cuarenta cuentas apiladas, que cada fila empiece en un
 * sitio distinto es lo que hace la lista ilegible.
 */
export function AdminAccountsPanel() {
  const { user } = useAuth()
  const { data, isPending } = useAdminUsers()
  const mutations = useAdminUserMutations()
  const [search, setSearch] = useState('')
  const [filtro, setFiltro] = useState<Filtro>('TODAS')

  const todas = useMemo(() => data?.content ?? [], [data])
  const cuentas = useMemo(
    () => todas.filter((u) => matches(u, search) && (filtro === 'TODAS' || u.role === filtro)),
    [todas, search, filtro],
  )

  const porRol = (role: Role) => todas.filter((u) => u.role === role).length
  const deshabilitadas = todas.filter((u) => !u.enabled).length

  if (isPending) return <Skeleton label="Cargando las cuentas…" lines={3} />

  return (
    <div className={styles.panel}>
      <div className={styles.toolbar}>
        <div className={styles.searchBox}>
          <Search size={14} strokeWidth={2.75} aria-hidden="true" />
          <input
            className={styles.searchInput}
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Buscar por nombre o correo"
            aria-label="Buscar cuentas"
          />
        </div>

        <div className={styles.filters}>
          <Chip active={filtro === 'TODAS'} onClick={() => setFiltro('TODAS')}>
            Todas {todas.length}
          </Chip>
          {ROLES.map((role) => (
            <Chip key={role} active={filtro === role} onClick={() => setFiltro(role)}>
              {ROLE_LABELS[role]} {porRol(role)}
            </Chip>
          ))}
        </div>
      </div>

      {deshabilitadas > 0 && (
        <p className={styles.summary}>
          {deshabilitadas === 1
            ? '1 cuenta deshabilitada: no puede entrar, pero conserva su historial.'
            : `${deshabilitadas} cuentas deshabilitadas: no pueden entrar, pero conservan su historial.`}
        </p>
      )}

      {cuentas.length === 0 ? (
        <EmptyState
          icon={<Search size={22} strokeWidth={2.75} />}
          title="Sin resultados"
          body="Ninguna cuenta coincide con esa búsqueda."
        />
      ) : (
        <ul className={styles.rows}>
          {cuentas.map((person) => {
            const isMe = person.id === user?.id
            return (
              <li
                key={person.id}
                className={[styles.row, styles.rowAccount, !person.enabled && styles.rowOff]
                  .filter(Boolean)
                  .join(' ')}
              >
                <Avatar user={person} size={30} />

                <span className={styles.cellName}>
                  {person.fullName}
                  {isMe && <span className={styles.you}>tú</span>}
                  {!person.enabled && (
                    <Tag variant="neutral" small>
                      Deshabilitada
                    </Tag>
                  )}
                </span>

                <span className={styles.cellMuted}>{person.email}</span>

                <Dropdown
                  trigger={({ toggle }) => (
                    <button className={styles.roleButton} onClick={toggle}>
                      {ROLE_LABELS[person.role]}
                    </button>
                  )}
                >
                  {(close) => (
                    <>
                      {ROLES.map((item) => (
                        <DropdownItem
                          key={item}
                          active={item === person.role}
                          onClick={() => {
                            close()
                            mutations.updateRole.mutate({ userId: person.id, role: item })
                          }}
                        >
                          {ROLE_LABELS[item]}
                        </DropdownItem>
                      ))}
                    </>
                  )}
                </Dropdown>

                <span className={styles.cellActions}>
                  {person.enabled ? (
                    <ConfirmDialog
                      trigger={
                        <Button
                          variant="ghost"
                          small
                          disabled={isMe}
                          aria-label={`Deshabilitar a ${person.fullName}`}
                        >
                          <UserX size={14} strokeWidth={2.75} />
                        </Button>
                      }
                      title={`Deshabilitar a ${person.fullName}`}
                      description="Deja de poder acceder, pero se conserva todo su historial."
                      confirmLabel="Deshabilitar"
                      onConfirm={() => mutations.disable.mutateAsync(person.id)}
                    />
                  ) : (
                    <Button
                      variant="ghost"
                      small
                      aria-label={`Reactivar a ${person.fullName}`}
                      onClick={() => mutations.enable.mutate(person.id)}
                    >
                      <UserCheck size={14} strokeWidth={2.75} />
                    </Button>
                  )}
                </span>
              </li>
            )
          })}
        </ul>
      )}

      <p className={styles.help}>
        El rol de cada equipo se gestiona desde ese equipo. Aquí solo se administra el rol de la
        cuenta en la plataforma y el alta o baja.
      </p>
    </div>
  )
}
