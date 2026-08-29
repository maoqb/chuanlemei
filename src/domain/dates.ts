import type { DateRange, PeriodPreset } from './types'

const dateFormatter = new Intl.DateTimeFormat('en-CA', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})

export function toDateInputValue(date = new Date()): string {
  return dateFormatter.format(date)
}

export function parseInputDate(value: string): Date {
  const [year, month, day] = value.split('-').map(Number)
  return new Date(year, month - 1, day)
}

export function addDays(value: string, days: number): string {
  const date = parseInputDate(value)
  date.setDate(date.getDate() + days)
  return toDateInputValue(date)
}

export function isDateInputToday(value: string, now = new Date()): boolean {
  return value === toDateInputValue(now)
}

export function formatReadableDate(value: string): string {
  const date = parseInputDate(value)
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'long',
    day: 'numeric',
    weekday: 'short',
  }).format(date)
}

export function formatShortDate(value: string): string {
  const date = parseInputDate(value)
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
  }).format(date)
}

export function getDateRangeForPreset(
  preset: PeriodPreset,
  now = new Date(),
  custom?: DateRange,
): DateRange {
  const end = toDateInputValue(now)

  if (preset === 'custom' && custom) {
    return normalizeRange(custom)
  }

  if (preset === 'all') {
    return { start: '1970-01-01', end }
  }

  if (preset === 'year') {
    const date = parseInputDate(end)
    date.setMonth(0, 1)
    return { start: toDateInputValue(date), end }
  }

  const days = preset === '7d' ? 6 : preset === '30d' ? 29 : 89
  return { start: addDays(end, -days), end }
}

export function normalizeRange(range: DateRange): DateRange {
  if (range.start <= range.end) {
    return range
  }
  return { start: range.end, end: range.start }
}

export function isWithinRange(value: string, range: DateRange): boolean {
  const normalized = normalizeRange(range)
  return value >= normalized.start && value <= normalized.end
}

export function enumerateDates(range: DateRange): string[] {
  const normalized = normalizeRange(range)
  const dates: string[] = []
  let cursor = normalized.start
  while (cursor <= normalized.end) {
    dates.push(cursor)
    cursor = addDays(cursor, 1)
  }
  return dates
}
