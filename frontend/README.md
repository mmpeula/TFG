# Frontend — PlanifIA

SPA en React + TypeScript + Vite contra la API de PlanifIA.

```bash
npm install
npm run dev        # http://localhost:5173
npm run build      # comprobacion de tipos + build de produccion
npm run lint
```

Vite hace de proxy de `/api` hacia `http://localhost:8080` (configurable con
`VITE_PROXY_TARGET`), así que en desarrollo no hay problemas de CORS. En Docker el
proxy lo hace Nginx y la aplicación se sirve en el puerto 3000.

## Las cuatro decisiones de producto

1. **La app no abre en el tablero, abre en un estado narrado.** «Hoy» empieza con un
   párrafo en lenguaje natural construido con las cifras reales del proyecto.
2. **Las señales son objetos de primera clase**: se resuelven o se descartan, no son
   avisos pasivos. Al resolver una, se navega al objeto implicado y se resalta 1 s.
3. **El tablero muestra presión, no solo tarjetas**: contador, límite de trabajo en
   curso y barra de presión por columna, más micro-badges en las tarjetas con problema.
4. **El copiloto es un banco de trabajo, no un chat**: se elige una acción y se ejecuta
   sobre el proyecto. Nada se escribe sin confirmación.

## Qué viene del backend y qué se calcula aquí

El backend no expone señales, métricas ni insights. Se derivan en el cliente a partir
de datos reales, y cada cifra que se muestra sale de esos cálculos:

| Pantalla | Origen |
|---|---|
| Auth, equipos, proyectos, tableros, columnas, tareas, comentarios, adjuntos, actividad | **API real** |
| Señales (`lib/signals.ts`) | Derivadas del tablero: columna por encima del WIP, fecha vencida, tarea sin tocar 4 días, tarea en curso sin responsable |
| Pulso: KPIs, series semanales, distribuciones e insights (`lib/metrics.ts`) | Calculados de las tareas y sus fechas |
| Generar plan | Llama a `POST /api/projects/{id}/ai-plan` (o a sus variantes con documento o descripción), que pide a **DeepSeek** el reparto completo de todos los sprints en una sola respuesta |

## Estructura

```
src/
├── styles/          tokens.css (valores cerrados) + base.css
├── types/api.ts     DTOs del backend
├── api/endpoints.ts capa HTTP completa
├── lib/             api-client (axios+JWT) · signals · metrics · roles · format
├── state/           context (hooks) + providers de sesion e interfaz
├── hooks/           useData (TanStack Query) · useShortcuts · useMediaQuery
├── components/      ui · shell · common · home · board · task · pulse · copilot
└── screens/         Login · Registro · Hoy · Equipos · Equipo · Proyectos ·
                     Proyecto · Tablero · Pulso · Copiloto · Configuracion
```

## Control de acceso en la interfaz

Cada respuesta trae `myRole` (el rol del usuario **en el equipo** dueño del recurso).
`lib/roles.ts` traduce eso a `canRead` / `canWrite` / `canManage`, y la UI oculta lo
que además el backend rechazaría: un OBSERVADOR no ve asas de arrastre, ni «Añadir
tarea», ni el formulario de comentarios. **Es cosmético**: la regla de verdad la
aplica el servidor.

## Sistema visual

Los valores de `src/styles/tokens.css` son cerrados: paleta terracota/salvia/neutros
sobre fondo `#f5ead8`, Caprasimo solo para títulos (peso 400), Figtree para el resto,
radios 8/16/28 y 999 px, y anillo de foco terracota en todo lo interactivo. Terracota
= atención y riesgo, salvia = éxito, neutros = estructura.

Detalles que conviene conocer antes de tocar estilos:

- Los `<button>` llevan `text-align: left` en `base.css`: el navegador los centra.
- Caprasimo solo tiene peso 400; los títulos pequeños en negrita van en Figtree 700.
- En móvil el tablero muestra una columna cada vez con un selector de estado, y las
  tarjetas se mueven con el menú «Mover a…» en vez de arrastrando (`useMediaQuery`).
