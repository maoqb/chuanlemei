import {
  addDays,
  enumerateDates,
  getDateRangeForPreset,
  isDateInputToday,
  normalizeRange,
  toDateInputValue,
} from './dates'

describe('date helpers', () => {
  it('formats local dates for date inputs', () => {
    expect(toDateInputValue(new Date(2026, 7, 29, 12))).toBe('2026-08-29')
  })

  it('builds inclusive preset ranges', () => {
    expect(getDateRangeForPreset('7d', new Date(2026, 7, 29, 12))).toEqual({
      start: '2026-08-23',
      end: '2026-08-29',
    })
  })

  it('normalizes custom ranges and enumerates dates', () => {
    const range = normalizeRange({ start: '2026-08-29', end: '2026-08-27' })
    expect(range).toEqual({ start: '2026-08-27', end: '2026-08-29' })
    expect(enumerateDates(range)).toEqual(['2026-08-27', '2026-08-28', '2026-08-29'])
    expect(addDays('2026-08-29', 1)).toBe('2026-08-30')
  })

  it('checks whether a record date is today', () => {
    const now = new Date(2026, 7, 29, 12)
    expect(isDateInputToday('2026-08-29', now)).toBe(true)
    expect(isDateInputToday('2026-08-28', now)).toBe(false)
  })
})
