import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { Loader2, Waves } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { PasswordInput } from '@/components/ui/PasswordInput'
import styles from './Auth.module.css'
import { auth } from '@/api/endpoints'
import { toErrorMessage } from '@/lib/api-client'
import { useAuth } from '@/state/context'
import { ROUTES, homeForRole } from '@/app/routes'

export function LoginScreen() {
  const navigate = useNavigate()
  const { signIn, isAuthenticated } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  const mutation = useMutation({
    mutationFn: auth.login,
    onSuccess: (response) => {
      signIn(response)
      navigate(homeForRole(response.user.role), { replace: true })
    },
  })

  if (isAuthenticated) return <Navigate to={ROUTES.home} replace />

  return (
    <div className={styles.page}>
      <div className={styles.wrap}>
        <div className={styles.brand}>
          <span className={styles.mark}><Waves size={22} strokeWidth={2.75} /></span>
          <span className={styles.name}>PlanifIA</span>
          <span className={styles.claim}>Gestion colaborativa de proyectos universitarios</span>
        </div>

        <div className={styles.card}>
          <h1 className={styles.title}>Acceder</h1>
          <p className={styles.subtitle}>Entra con tu cuenta para ver tus tableros.</p>

          <form
            className={styles.form}
            onSubmit={(event) => {
              event.preventDefault()
              mutation.mutate({ email, password })
            }}
          >
            <div>
              <label className={styles.label} htmlFor="email">Email</label>
              <input
                id="email" type="email" autoComplete="email" required className={styles.control}
                value={email} onChange={(event) => setEmail(event.target.value)}
                placeholder="tu.correo@uni.es"
              />
            </div>

            <div>
              <label className={styles.label} htmlFor="password">Contraseña</label>
              <PasswordInput
                id="password" autoComplete="current-password" required className={styles.control}
                value={password} onChange={(event) => setPassword(event.target.value)}
              />
            </div>

            {mutation.isError && <div className={styles.error}>{toErrorMessage(mutation.error)}</div>}

            <Button type="submit" block disabled={mutation.isPending}>
              {mutation.isPending && <Loader2 size={14} strokeWidth={2.75} />}
              Entrar
            </Button>
          </form>
        </div>

        <p className={styles.foot}>
          ¿No tienes cuenta? <Link className={styles.link} to={ROUTES.register}>Registrate</Link>
        </p>
      </div>
    </div>
  )
}
