import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { Loader2, Waves } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import styles from './Auth.module.css'
import { auth } from '@/api/endpoints'
import { toErrorMessage } from '@/lib/api-client'
import { useAuth } from '@/state/context'
import { ROUTES } from '@/app/routes'

export function RegisterScreen() {
  const navigate = useNavigate()
  const { signIn, isAuthenticated } = useAuth()
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  const mutation = useMutation({
    mutationFn: auth.register,
    onSuccess: (response) => {
      signIn(response)
      navigate(ROUTES.home, { replace: true })
    },
  })

  if (isAuthenticated) return <Navigate to={ROUTES.home} replace />

  return (
    <div className={styles.page}>
      <div className={styles.wrap}>
        <div className={styles.brand}>
          <span className={styles.mark}><Waves size={22} strokeWidth={2.75} /></span>
          <span className={styles.name}>PlanifIA</span>
        </div>

        <div className={styles.card}>
          <h1 className={styles.title}>Crear cuenta</h1>
          <p className={styles.subtitle}>
            Tu rol no se elige aqui: dentro de cada workspace tendras el que te asigne su responsable.
          </p>

          <form
            className={styles.form}
            onSubmit={(event) => {
              event.preventDefault()
              mutation.mutate({ fullName, email, password })
            }}
          >
            <div>
              <label className={styles.label} htmlFor="fullName">Nombre completo</label>
              <input
                id="fullName" required maxLength={150} className={styles.control}
                value={fullName} onChange={(event) => setFullName(event.target.value)}
                placeholder="Ana Garcia"
              />
            </div>

            <div>
              <label className={styles.label} htmlFor="email">Email</label>
              <input
                id="email" type="email" autoComplete="email" required className={styles.control}
                value={email} onChange={(event) => setEmail(event.target.value)}
                placeholder="ana@uni.es"
              />
            </div>

            <div>
              <label className={styles.label} htmlFor="password">Contrasena</label>
              <input
                id="password" type="password" autoComplete="new-password" required minLength={8}
                className={styles.control}
                value={password} onChange={(event) => setPassword(event.target.value)}
              />
              <p className={styles.hint}>Minimo 8 caracteres.</p>
            </div>

            {mutation.isError && <div className={styles.error}>{toErrorMessage(mutation.error)}</div>}

            <Button type="submit" block disabled={mutation.isPending}>
              {mutation.isPending && <Loader2 size={14} strokeWidth={2.75} />}
              Crear cuenta
            </Button>
          </form>
        </div>

        <p className={styles.foot}>
          ¿Ya tienes cuenta? <Link className={styles.link} to={ROUTES.login}>Inicia sesion</Link>
        </p>
      </div>
    </div>
  )
}
