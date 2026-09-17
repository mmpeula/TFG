import { useMemo, useState } from 'react'
import { Navigate } from 'react-router-dom'
import { ShieldCheck, UserCheck, UserX } from 'lucide-react'
import { HomeShell } from '@/components/shell/HomeShell'
import { Avatar } from '@/components/ui/Avatar'
import { Button } from '@/components/ui/Button'
import { Tag } from '@/components/ui/Tag'
import { Dropdown, DropdownItem } from '@/components/ui/Controls'
import { EmptyState } from '@/components/ui/EmptyState'
import { Skeleton } from '@/components/ui/Skeleton'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import styles from './AdminScreen.module.css'
import { useAdminUserMutations, useAdminUsers } from '@/hooks/useData'
import { useAuth } from '@/state/context'
import { isAdmin, ROLE_LABELS } from '@/lib/roles'
import { ROUTES } from '@/app/routes'
import type { Role, UserResponse } from '@/types/api'

const ROLES: Role[] = ['RESPONSABLE', 'MIEMBRO', 'OBSERVADOR', 'ADMIN']

function matches(user: UserResponse, term: string): boolean {
  const q = term.trim().toLowerCase()
  if (!q) return true
  return user.fullName.toLowerCase().includes(q) || user.email.toLowerCase().includes(q)
}

/**
 * Cuentas de toda la plataforma: rol global y alta o baja de una cuenta.
 *
 * Distinto de MembersScreen a proposito: ese gestiona el rol de equipo de un
 * workspace concreto; este gestiona el rol global (RF-02), que no depende de
 * ningun equipo y solo ve quien tiene rol ADMIN.
 */
export function AdminScreen() {
  const { user } = useAuth()
  const { data, isPending } = useAdminUsers()
  const mutations = useAdminUserMutations()
  const [search, setSearch] = useState('')

  const people = useMemo(() => (data?.content ?? []).filter((u) => matches(u, search)), [data, search])

  if (!isAdmin(user?.role)) {
    return <Navigate to={ROUTES.workspaces} replace />
  }

  return (
    <HomeShell>
      {isPending ? (
        <Skeleton label="Cargando las cuentas…" lines={3} />
      ) : (
        <div className={styles.layout}>
          <h1 style={{ fontSize: 18, fontWeight: 700 }}>Administración de la plataforma</h1>

          <div className={styles.search}>
            <label className={styles.label} htmlFor="admin-search">
              Buscar
            </label>
            <input
              id="admin-search"
              className={styles.control}
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Nombre o correo"
            />
          </div>

          {people.length === 0 ? (
            <EmptyState
              icon={<ShieldCheck size={22} strokeWidth={2.75} />}
              title="Sin resultados"
              body="Ninguna cuenta coincide con esa búsqueda."
            />
          ) : (
            <ul className={styles.users}>
              {people.map((person) => {
                const isMe = person.id === user?.id
                return (
                  <li
                    key={person.id}
                    className={[styles.user, !person.enabled && styles.userDisabled].filter(Boolean).join(' ')}
                  >
                    <Avatar user={person} size={34} />
                    <div className={styles.identity}>
                      <div className={styles.name}>
                        {person.fullName}
                        {isMe && <span className={styles.you}> (tú)</span>}
                      </div>
                      <div className={styles.mail}>{person.email}</div>
                    </div>

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

                    {!person.enabled && (
                      <Tag variant="neutral" small>
                        Deshabilitada
                      </Tag>
                    )}

                    {person.enabled ? (
                      <ConfirmDialog
                        trigger={
                          <Button variant="ghost" small disabled={isMe} aria-label={`Deshabilitar a ${person.fullName}`}>
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
                  </li>
                )
              })}
            </ul>
          )}

          <p className={styles.help}>
            El rol de cada equipo se gestiona desde ese equipo. Aquí solo se administra el rol
            global de la plataforma, reservado a quien la administra, y el alta o baja de cuentas.
          </p>
        </div>
      )}
    </HomeShell>
  )
}
