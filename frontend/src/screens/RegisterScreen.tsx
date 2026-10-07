import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { Segmented } from '@/components/ui/Controls'
import { PasswordInput } from '@/components/ui/PasswordInput'
import { AuthLayout, AuthSubmit } from './AuthLayout'
import styles from './Auth.module.css'
import { auth } from '@/api/endpoints'
import { useAuth } from '@/state/context'
import { ROUTES } from '@/app/routes'

type CuentaDe = 'MIEMBRO' | 'DOCENTE'

export function RegisterScreen() {
  const navigate = useNavigate()
  const { signIn, isAuthenticated } = useAuth()
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [role, setRole] = useState<CuentaDe>('MIEMBRO')
  // Solo se avisa una vez que la persona ha llegado a escribir algo en el
  // segundo campo: mostrar el error mientras el primero sigue vacio o a medio
  // escribir no ayuda, solo estorba.
  const passwordsMismatch = confirmPassword.length > 0 && password !== confirmPassword

  const mutation = useMutation({
    mutationFn: auth.register,
    onSuccess: (response) => {
      signIn(response)
      navigate(ROUTES.home, { replace: true })
    },
  })

  if (isAuthenticated) return <Navigate to={ROUTES.home} replace />

  return (
    <AuthLayout
      title="Crear cuenta"
      subtitle="Dentro de cada espacio de trabajo tendrás además el rol que te asigne su responsable."
      foot={<>¿Ya tienes cuenta? <Link className={styles.link} to={ROUTES.login}>Inicia sesión</Link></>}
    >
      <form
        className={styles.form}
        onSubmit={(event) => {
          event.preventDefault()
          if (passwordsMismatch) return
          mutation.mutate({ fullName, email, password, role })
        }}
      >
        <div>
          <span className={styles.label}>Te registras como</span>
          <Segmented
            ariaLabel="Te registras como"
            value={role}
            onChange={setRole}
            options={[
              { value: 'MIEMBRO', label: 'Alumno' },
              { value: 'DOCENTE', label: 'Docente' },
            ]}
          />
          <p className={styles.hint}>
            {role === 'DOCENTE'
              ? 'Sigues los espacios de trabajo a los que te añadan, en modo lectura.'
              : 'Trabajas en los espacios de trabajo a los que te añadan.'}
          </p>
        </div>

        <div>
          <label className={styles.label} htmlFor="fullName">Nombre completo</label>
          <input
            id="fullName" required maxLength={150} className={styles.control}
            value={fullName} onChange={(event) => setFullName(event.target.value)}
            placeholder="Ana García"
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
          <label className={styles.label} htmlFor="password">Contraseña</label>
          <PasswordInput
            id="password" autoComplete="new-password" required minLength={8}
            className={styles.control}
            value={password} onChange={(event) => setPassword(event.target.value)}
          />
          <p className={styles.hint}>Mínimo 8 caracteres.</p>
        </div>

        <div>
          <label className={styles.label} htmlFor="confirmPassword">Repite la contraseña</label>
          <PasswordInput
            id="confirmPassword" autoComplete="new-password" required minLength={8}
            className={styles.control}
            value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)}
            aria-invalid={passwordsMismatch}
          />
          {passwordsMismatch
            ? <p className={styles.fieldError}>Las dos contraseñas no coinciden.</p>
            : <p className={styles.hint}>Escríbela igual que arriba.</p>}
        </div>

        <AuthSubmit error={mutation.error} pending={mutation.isPending} disabled={passwordsMismatch}>
          Crear cuenta
        </AuthSubmit>
      </form>
    </AuthLayout>
  )
}
