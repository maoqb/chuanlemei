import { buildDashboardStats, getGarmentWearCount, getRecordsForGarment } from './stats'
import type { Garment, WearRecord } from './types'

describe('stats helpers', () => {
  it('builds period, garment and outfit stats', () => {
    const garments: Garment[] = [
      garment('top-1', '白T', 'top'),
      garment('bottom-1', '牛仔裤', 'bottom'),
      garment('shoes-1', '白鞋', 'shoes'),
    ]
    const records: WearRecord[] = [
      record('wear-1', '2026-08-29', { top: 'top-1', bottom: 'bottom-1', shoes: 'shoes-1' }),
      record('wear-2', '2026-08-01', { top: 'top-1' }),
    ]

    const stats = buildDashboardStats(garments, records, {
      start: '2026-08-23',
      end: '2026-08-29',
    })

    expect(stats.totalRecords).toBe(1)
    expect(stats.totalGarmentWears).toBe(3)
    expect(stats.categoryCounts).toEqual({ top: 1, bottom: 1, shoes: 1 })
    expect(stats.garmentStats.find((item) => item.garment.id === 'top-1')).toMatchObject({
      wearCount: 2,
      rangeCount: 1,
      lastWornAt: '2026-08-29',
    })
    expect(stats.outfitStats[0]).toMatchObject({ count: 1 })
    expect(stats.dailyCounts.find((item) => item.date === '2026-08-29')?.count).toBe(1)
  })

  it('returns per garment records newest first', () => {
    const records = [
      record('wear-1', '2026-08-28', { top: 'top-1' }),
      record('wear-2', '2026-08-29', { top: 'top-1' }),
      record('wear-3', '2026-08-29', { shoes: 'shoes-1' }),
    ]

    expect(getGarmentWearCount(records, 'top-1')).toBe(2)
    expect(getRecordsForGarment(records, 'top-1').map((item) => item.id)).toEqual(['wear-2', 'wear-1'])
  })
})

function garment(id: string, name: string, category: Garment['category']): Garment {
  return {
    id,
    name,
    category,
    color: '#ffffff',
    createdAt: '2026-08-29T00:00:00.000Z',
    updatedAt: '2026-08-29T00:00:00.000Z',
  }
}

function record(id: string, wornAt: string, garmentIds: WearRecord['garmentIds']): WearRecord {
  return {
    id,
    wornAt,
    garmentIds,
    capturedAt: `${wornAt}T08:00:00.000Z`,
    evidencePhotoDataUrl: 'data:image/jpeg;base64,stub',
    recognition: [],
  }
}
