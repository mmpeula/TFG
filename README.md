<p align="center">
  <img src="frontend/public/logo.png" alt="Logo de PlanifIA" width="96">
</p>

<h1 align="center">PlanifIA</h1>

<p align="center">
  <b>Diseño y desarrollo de una plataforma web Kanban para la planificación inteligente de equipos universitarios</b>
</p>


---

PlanifIA es una plataforma web para organizar prácticas y trabajos de clase en equipo.
A partir del enunciado de una práctica, la IA genera un plan de trabajo por sprints.
El equipo sigue ese plan en un tablero Kanban y el docente consulta el progreso en tiempo real.

## Inicio rápido

Solo hace falta **Docker** con **Docker Compose**.

```bash
git clone https://github.com/mmpeula/TFG.git
cd TFG
cp .env.example .env        # rellena las variables obligatorias (ver Configuración)
docker compose up --build
```

> La primera vez que se arranca, Oracle tarda varios minutos en crear la base de datos.

Cuando la pila esté levantada, los servicios quedan disponibles en estas direcciones:

| Servicio | Dirección |
|---|---|
| Aplicación web | http://localhost:3000 |
| API REST | http://localhost:8080/api |
| Documentación de la API (Swagger UI) | http://localhost:8080/swagger-ui.html |
| Correos de invitación (Mailpit) | http://localhost:8025 |

## Qué hace

### Planificar

- **Plan generado con IA.** Se sube el guion de la práctica (PDF, Word o texto) y la IA
  propone un backlog con épicas, tareas, dependencias, estimaciones y responsables. El
  servidor calcula las fechas de cada sprint y reparte la carga entre los miembros reales
  del equipo.
- **Documentos escaneados.** Las páginas escaneadas y las imágenes del documento también
  se leen, con el modelo de visión.

### Trabajar

- **Tablero Kanban.** Las tareas se mueven arrastrándolas y cada columna admite un límite
  de trabajo en curso. El tablero avisa de las tareas vencidas, de las que están paradas y
  de las que están en curso sin responsable.
- **Plan por sprints.** Muestra tus tareas o las de todo el equipo y compara las horas
  reales con las estimadas.
- **Asistente.** Chat con IA que conoce el contexto de cada espacio de trabajo.

### Seguir el progreso

- **Panel de seguimiento.** KPIs, gráficas de evolución e indicadores del progreso del equipo.

### Gestionar

- **Equipos y roles.** Invitaciones por correo y cuatro roles: administrador, docente,
  responsable y alumno. Los permisos se comprueban siempre en el servidor.
- **Administración.** Gestión de todas las cuentas y espacios de la plataforma.

## Configuración

Toda la configuración está en el fichero `.env` de la raíz. Estas son las variables que
hay que rellenar:

| Variable | Valor |
|---|---|
| `ORACLE_PASSWORD` | Contraseña aleatoria del usuario `SYSTEM` de Oracle |
| `DB_USERNAME` | Usuario de la aplicación en la base de datos (por defecto `kanban`) |
| `DB_PASSWORD` | Contraseña aleatoria de ese usuario |
| `JWT_SECRET` | Mínimo 32 caracteres. Se genera con `openssl rand -base64 48` |
| `ADMIN_EMAIL` | Cuenta de administrador que se crea en el primer arranque (por defecto `admin@planifia.local`) |
| `ADMIN_PASSWORD` | Contraseña de esa cuenta. No hay contraseña por defecto |
| `DEEPSEEK_API_KEY` | *Opcional.* Se consigue en [platform.deepseek.com](https://platform.deepseek.com) |

Sin la API key de DeepSeek la aplicación funciona igual, pero no genera planes ni
responde el asistente. El resto de variables son opcionales y la plantilla
[`.env.example`](.env.example) explica cada una.

Autor: Marcos Medina Peula
