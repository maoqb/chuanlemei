import { categoryOrder, type DateRange, type Garment, type GarmentCategory, type WearRecord } from './types'
import { enumerateDates, isWithinRange } from './dates'

export interface GarmentStat {
  garment: Garment
  wearCount: number
  lastWornAt?: string
  rangeCount: number
}

export interface OutfitStat {
  key: string
  garmentIds: string[]
  count: number
  lastWornAt?: string
}

export interface DashboardStats {
  totalRecords: number
  totalGarmentWears: number
  activeGarments: number
  categoryCounts: Record<GarmentCategory, number>
  dailyCounts: Array<{ date: string; count: number }>
  garmentStats: GarmentStat[]
  outfitStats: OutfitStat[]
}

export function buildDashboardStats(
  garments: Garment[],
  records: WearRecord[],
  range: DateRange,
): DashboardStats {
  const garmentsById = new Map(garments.map((garment) => [garment.id, garment]))
  const activeGarments = garments.filter((garment) => !garment.archivedAt)
  const recordsInRange = records.filter((record) => isWithinRange(record.wornAt, range))
  const perGarment = new Map<string, { rangeCount: number; totalCount: number; lastWornAt?: string }>()
  const outfitMap = new Map<string, OutfitStat>()
  const dailyMap = new Map(enumerateDates(range).map((date) => [date, 0]))

  for (const garment of garments) {
    perGarment.set(garment.id, { rangeCount: 0, totalCount: 0 })
  }

  for (const record of records) {
    const ids = categoryOrder
      .map((category) => record.garmentIds[category])
      .filter((id): id is string => Boolean(id))

    for (const id of ids) {
      const current = perGarment.get(id) ?? { rangeCount: 0, totalCount: 0 }
      current.totalCount += 1
      current.lastWornAt = maxDate(current.lastWornAt, record.wornAt)
      if (isWithinRange(record.wornAt, range)) {
        current.rangeCount += 1
      }
      perGarment.set(id, current)
    }

    if (isWithinRange(record.wornAt, range)) {
      dailyMap.set(record.wornAt, (dailyMap.get(record.wornAt) ?? 0) + 1)
      const outfitKey = ids.sort().join('|')
      if (outfitKey) {
        const outfit = outfitMap.get(outfitKey) ?? {
          key: outfitKey,
          garmentIds: ids,
          count: 0,
        }
        outfit.count += 1
        outfit.lastWornAt = maxDate(outfit.lastWornAt, record.wornAt)
        outfitMap.set(outfitKey, outfit)
      }
    }
  }

  const categoryCounts = categoryOrder.reduce(
    (result, category) => ({ ...result, [category]: 0 }),
    {} as Record<GarmentCategory, number>,
  )

  for (const record of recordsInRange) {
    for (const category of categoryOrder) {
      const garmentId = record.garmentIds[category]
      if (garmentId && garmentsById.has(garmentId)) {
        categoryCounts[category] += 1
      }
    }
  }

  const garmentStats = garments
    .map((garment) => {
      const counts = perGarment.get(garment.id) ?? { rangeCount: 0, totalCount: 0 }
      return {
        garment,
        wearCount: counts.totalCount,
        lastWornAt: counts.lastWornAt,
        rangeCount: counts.rangeCount,
      }
    })
    .sort((left, right) => right.rangeCount - left.rangeCount || right.wearCount - left.wearCount)

  return {
    totalRecords: recordsInRange.length,
    totalGarmentWears: Object.values(categoryCounts).reduce((sum, value) => sum + value, 0),
    activeGarments: activeGarments.length,
    categoryCounts,
    dailyCounts: Array.from(dailyMap.entries()).map(([date, count]) => ({ date, count })),
    garmentStats,
    outfitStats: Array.from(outfitMap.values()).sort((left, right) => right.count - left.count),
  }
}

export function getRecordsForGarment(records: WearRecord[], garmentId: string): WearRecord[] {
  return records
    .filter((record) => Object.values(record.garmentIds).includes(garmentId))
    .sort((left, right) => right.wornAt.localeCompare(left.wornAt))
}

export function getGarmentWearCount(records: WearRecord[], garmentId: string): number {
  return getRecordsForGarment(records, garmentId).length
}

function maxDate(left: string | undefined, right: string): string {
  if (!left || right > left) {
    return right
  }
  return left
}
