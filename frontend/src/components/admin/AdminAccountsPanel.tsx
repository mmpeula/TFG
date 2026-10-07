import { useMemo, useState } from 'react'
import { ChevronDown, Search, UserCheck, UserX } from 'lucide-react'
import { AdminSearch } from './AdminSearch'
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
import { formatDate, matchesSearch } from '@/lib/format'
import type { Role, UserResponse } from '@/types/api'

/** Los tres tipos de cuenta, para filtrar. RESPONSABLE no esta: se es de un equipo, no de la plataforma. */
const ROLES: Role[] = ['MIEMBRO', 'DOCENTE', 'ADMIN']

type Filtro = Role | 'TODAS'

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
  // A quien se esta a punto de subir de rol, y a cual: no tiene vuelta atras, asi que se confirma.
  const [promoting, setPromoting] = useState<{ person: UserResponse; role: 'DOCENTE' | 'ADMIN' } | null>(null)
  const [filtro, setFiltro] = useState<Filtro>('TODAS')

  const todas = useMemo(() => data?.content ?? [], [data])
  const cuentas = useMemo(
    () => todas.filter((u) => matchesSearch(search, u.fullName, u.email) && (filtro === 'TODAS' || u.role === filtro)),
    [todas, search, filtro],
  )

  const porRol = (role: Role) => todas.filter((u) => u.role === role).length
  const deshabilitadas = todas.filter((u) => !u.enabled).length

  if (isPending) return <Skeleton label="Cargando las cuentas…" lines={3} />

  return (
    <div className={styles.panel}>
      <div className={styles.toolbar}>
        <AdminSearch value={search} onChange={setSearch} placeholder="Buscar por nombre o correo" label="Buscar cuentas" />

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
                <Avatar user={person} avatarUrl={person.avatarUrl} size={30} />

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

                <span className={styles.cellDate} title="Fecha de alta">{formatDate(person.createdAt)}</span>

                {/* Los roles solo suben: alumno a docente o administrador, docente a administrador.
                    Un administrador ya esta arriba del todo y no se degrada, asi que su rol no es un menu. */}
                {person.role === 'ADMIN' ? (
                  <span className={styles.roleStatic}>{ROLE_LABELS[person.role]}</span>
                ) : (
                  <Dropdown
                    trigger={({ toggle }) => (
                      <button className={styles.roleButton} onClick={toggle}>
                        {ROLE_LABELS[person.role]}
                        <ChevronDown size={14} strokeWidth={2.75} aria-hidden="true" />
                      </button>
                    )}
                  >
                    {(close) => (
                      <>
                        {person.role !== 'DOCENTE' && (
                          <DropdownItem
                            onClick={() => {
                              close()
                              setPromoting({ person, role: 'DOCENTE' })
                            }}
                          >
                            Hacer docente
                          </DropdownItem>
                        )}
                        <DropdownItem
                          onClick={() => {
                            close()
                            setPromoting({ person, role: 'ADMIN' })
                          }}
                        >
                          Hacer administrador
                        </DropdownItem>
                      </>
                    )}
                  </Dropdown>
                )}

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

      <ConfirmDialog
        open={promoting !== null}
        onOpenChange={(open) => { if (!open) setPromoting(null) }}
        title={`${promoting?.role === 'DOCENTE' ? 'Hacer docente' : 'Hacer administrador'} a ${promoting?.person.fullName ?? ''}`}
        description={promoting?.role === 'DOCENTE'
          ? 'Seguirá viendo los mismos espacios de trabajo, y podrá seguir como docente los que le añadan. No se puede volver a hacer alumno.'
          : 'Podrá administrar todas las cuentas de la plataforma. Un administrador no se puede volver a degradar.'}
        confirmLabel={promoting?.role === 'DOCENTE' ? 'Hacer docente' : 'Hacer administrador'}
        onConfirm={() => mutations.updateRole.mutateAsync({ userId: promoting!.person.id, role: promoting!.role })}
      />

      <p className={styles.help}>
        El rol de cada equipo se gestiona desde ese equipo. Aquí los roles de cuenta solo suben:
        alumno a docente o administrador, y docente a administrador. No hay vuelta atrás. También
        se da de alta o de baja, y la plataforma siempre conserva al menos un administrador.
      </p>
    </div>
  )
}
