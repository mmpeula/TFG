/**
 * Las horas de una tarea se escriben y se ven siempre en horas, con decimales:
 * «1,5» es una hora y media, «0,5» media hora. Sin minutos ni dias, para que no
 * haya dudas de que significa cada cifra. Vale coma o punto, y una «h» detras.
 */

/** Horas que representa el texto, redondeadas a dos decimales; null si no es un numero mayor que cero. */
export function parseHours(raw: string): number | null {
  const text = raw.trim().toLowerCase().replace(/\s*(h|horas?)$/, '').replace(',', '.')
  if (!/^\d+(\.\d+)?$/.test(text)) return null
  const hours = Number(text)
  return hours > 0 && hours <= 9999 ? Math.round(hours * 100) / 100 : null
}

/** «1.5» → «1,5»: el numero tal cual se escribe en espanol, sin la unidad. */
export function hoursNumber(hours: number): string {
  return hours.toLocaleString('es-ES', { maximumFractionDigits: 2 })
}

/** «1.5» → «1,5 h». */
export function formatHours(hours: number): string {
  return `${hoursNumber(hours)} h`
}
