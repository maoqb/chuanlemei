export type GarmentCategory = 'top' | 'bottom' | 'shoes'

export type PeriodPreset = '7d' | '30d' | '90d' | 'year' | 'all' | 'custom'

export type GarmentMap = Partial<Record<GarmentCategory, string>>

export interface ImageSignature {
  averageRgb: [number, number, number]
  histogram: number[]
}

export interface Garment {
  id: string
  name: string
  category: GarmentCategory
  color: string
  brand?: string
  note?: string
  imageDataUrl?: string
  signature?: ImageSignature
  createdAt: string
  updatedAt: string
  archivedAt?: string
}

export interface RecognitionCandidate {
  garmentId: string
  garmentName: string
  category: GarmentCategory
  confidence: number
}

export interface RecognitionSlot {
  category: GarmentCategory
  selectedGarmentId?: string
  confidence: number
  alternatives: RecognitionCandidate[]
}

export interface WearRecord {
  id: string
  wornAt: string
  capturedAt: string
  evidencePhotoDataUrl: string
  garmentIds: GarmentMap
  recognition: RecognitionSlot[]
  note?: string
}

export interface DateRange {
  start: string
  end: string
}

export interface AppExport {
  version: 1
  exportedAt: string
  garments: Garment[]
  wearRecords: WearRecord[]
}

export const categoryLabels: Record<GarmentCategory, string> = {
  top: '上衣',
  bottom: '裤子',
  shoes: '鞋',
}

export const categoryOrder: GarmentCategory[] = ['top', 'bottom', 'shoes']

export const categoryAccent: Record<GarmentCategory, string> = {
  top: '#2f6f73',
  bottom: '#715c9f',
  shoes: '#9a5b38',
}
