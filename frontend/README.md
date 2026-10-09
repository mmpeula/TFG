# Frontend — PlanifIA

SPA en React + TypeScript + Vite contra la API de PlanifIA.

```bash
npm install
npm run dev        # http://localhost:5173
npm run build      # comprobacion de tipos + build de produccion
npm run lint       # oxlint
```

Vite hace de proxy de `/api` hacia `http://localhost:8080` (configurable con
`VITE_PROXY_TARGET`), así que en desarrollo no hay problemas de CORS. En Docker el
proxy lo hace Nginx y la aplicación se sirve en el puerto 3000.

## Pantallas

| Ruta | Pantalla | Quién |
|---|---|---|
| `/login`, `/registro` | Inicio de sesión y alta (como alumno o como docente) | Cualquiera |
| `/workspaces` | Mis espacios de trabajo: los propios y, si se sigue alguno como docente, la tarjeta del panel docente | Alumno y docente |
| `/workspaces/:id/plan` | Plan por sprints (mis tareas o todo el equipo; el docente siempre ve todo el equipo) | Miembros del espacio |
| `/workspaces/:id/tablero` | Tablero Kanban con señales y límites de trabajo en curso | Miembros del espacio |
| `/workspaces/:id/generar-plan` | Generar plan con IA a partir de un documento o una descripción | Responsable del espacio |
| `/workspaces/:id/miembros` | Equipo, roles e invitaciones | Miembros del espacio |
| `/workspaces/:id/ajustes` | Calendario, foto y actividad del espacio (editable solo por el responsable) | Miembros del espacio |
| `/workspaces/:id/seguimiento` | Métricas y progreso | Miembros del espacio |
| `/docente` | Panel docente: los espacios que se siguen como docente, con sus alertas | Docente |
| `/administracion` | Cuentas y espacios de toda la plataforma | Administrador |
| `/configuracion` | Mi cuenta: nombre, foto y contraseña | Cualquiera con sesión |
| `/invitacion/:token` | Responder a una invitación | La persona invitada |

`/workspaces/:id` lleva al plan de ese espacio; cualquier ruta desconocida, a
`/workspaces`.

## Qué viene del backend y qué se calcula aquí

| Dato | Origen |
|---|---|
| Cuentas, espacios, equipos, invitaciones, tableros, columnas, tareas, comentarios, adjuntos, actividad | **API real** |
| Señales (`lib/signals.ts`) | Derivadas del tablero: columna por encima del WIP, fecha vencida, tarea sin tocar, tarea en curso sin responsable |
| Panel de seguimiento: KPIs, series e insights (`lib/metrics.ts`) | Calculados a partir de las tareas y sus fechas |
| Plan por sprints (`lib/sprints.ts`) | Calculado a partir de las fechas del espacio y de las tareas |
| Generar plan y asistente | `POST /api/projects/{id}/ai-plan…` y `POST /api/workspaces/{id}/ai/chat`, que el backend resuelve con **DeepSeek** |

## Estructura

```
src/
├── styles/          tokens.css (sistema de diseño) + base.css
├── types/api.ts     DTOs del backend
├── api/endpoints.ts capa HTTP completa
├── lib/             api-client (axios + JWT) · signals · metrics · sprints · roles · format
├── state/           sesión, interfaz, trabajos de generación de plan, borradores y asistente
├── hooks/           useData (TanStack Query) · useShortcuts · useMediaQuery · useDebouncedValue
├── components/      ui · shell · common · workspace · board · task · tracking ·
│                    generator · assistant · admin · home
│                    (board/TaskCard es la tarjeta de tarea del tablero y del plan)
└── screens/         una por ruta de la tabla de arriba
```

## Control de acceso en la interfaz

Hay dos planos de rol. El **de cuenta** (`user.role`: alumno, docente o administrador)
solo decide a qué zona se entra: el administrador tiene su panel; alumno y docente
comparten la de espacios de trabajo. El **de cada espacio** (`myRole` en las respuestas:
responsable, alumno o docente) decide qué se puede hacer dentro: `lib/roles.ts` lo
traduce a `canWrite` / `canManage` / `isTeacher`. Una misma cuenta docente puede ser
responsable de sus propios espacios y solo observar los de otros.

La interfaz oculta lo que además el backend rechazaría (un docente no ve asas de
arrastre, ni «Añadir tarea», ni el formulario de comentarios). **Es cosmético**: la
regla de verdad la aplica el servidor.

## Sistema visual

Los valores de `src/styles/tokens.css` son cerrados y su cabecera explica el porqué: el
tablero se lee como un log de git. Lo que se mide (horas, fechas, ids) va en
monoespacio; lo que se escribe, en la tipografía del sistema. Los cuatro colores tienen
un trabajo fijo y no se usan para otra cosa: **azul** para la acción principal, el foco
y todo lo relacionado con la IA; **verde** para lo que va por debajo de lo estimado o
está terminado; **rojo** para lo que va por encima, atrasado o crítico; **ámbar** para
lo pendiente de revisar.

Detalles que conviene conocer antes de tocar estilos:

- Los `<button>` llevan `text-align: left` en `base.css`: el navegador los centra.
- Casi todo se separa con líneas de 1 px; las sombras quedan para lo que flota de
  verdad (modales, la tarjeta que se arrastra, el asistente).
- En móvil el tablero muestra una columna cada vez con un selector de estado, y las
  tarjetas se mueven con el menú «Mover a…» en vez de arrastrando (`useMediaQuery`).
