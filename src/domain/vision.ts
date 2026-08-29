import { categoryOrder, type Garment, type ImageSignature, type RecognitionSlot } from './types'

const CHANNEL_BINS = 4
const HISTOGRAM_SIZE = CHANNEL_BINS * CHANNEL_BINS * CHANNEL_BINS

export function createImageSignatureFromPixels(
  pixels: Uint8ClampedArray,
  width: number,
  height: number,
): ImageSignature {
  const histogram = Array.from({ length: HISTOGRAM_SIZE }, () => 0)
  let totalWeight = 0
  let red = 0
  let green = 0
  let blue = 0
  const centerX = width / 2
  const centerY = height / 2
  const maxDistance = Math.hypot(centerX, centerY) || 1

  for (let index = 0; index < pixels.length; index += 4) {
    const alpha = pixels[index + 3] / 255
    if (alpha < 0.15) {
      continue
    }

    const pixelIndex = index / 4
    const x = pixelIndex % width
    const y = Math.floor(pixelIndex / width)
    const centerWeight = 1 - Math.hypot(x - centerX, y - centerY) / maxDistance
    const weight = Math.max(0.35, centerWeight) * alpha

    const r = pixels[index]
    const g = pixels[index + 1]
    const b = pixels[index + 2]
    const bucket = colorBucket(r, g, b)
    histogram[bucket] += weight
    red += r * weight
    green += g * weight
    blue += b * weight
    totalWeight += weight
  }

  if (totalWeight === 0) {
    return {
      averageRgb: [0, 0, 0],
      histogram,
    }
  }

  return {
    averageRgb: [
      Math.round(red / totalWeight),
      Math.round(green / totalWeight),
      Math.round(blue / totalWeight),
    ],
    histogram: histogram.map((value) => value / totalWeight),
  }
}

export async function createImageSignature(dataUrl: string): Promise<ImageSignature> {
  const image = await loadImage(dataUrl)
  const canvas = document.createElement('canvas')
  const size = 72
  canvas.width = size
  canvas.height = size
  const context = canvas.getContext('2d', { willReadFrequently: true })
  if (!context) {
    throw new Error('无法读取图片像素')
  }

  context.fillStyle = '#ffffff'
  context.fillRect(0, 0, size, size)
  const ratio = Math.min(size / image.width, size / image.height)
  const width = image.width * ratio
  const height = image.height * ratio
  const x = (size - width) / 2
  const y = (size - height) / 2
  context.drawImage(image, x, y, width, height)
  const imageData = context.getImageData(0, 0, size, size)
  return createImageSignatureFromPixels(imageData.data, size, size)
}

export function compareSignatures(left: ImageSignature, right: ImageSignature): number {
  const histogramSimilarity = left.histogram.reduce(
    (sum, value, index) => sum + Math.min(value, right.histogram[index] ?? 0),
    0,
  )
  const colorDistance = Math.hypot(
    left.averageRgb[0] - right.averageRgb[0],
    left.averageRgb[1] - right.averageRgb[1],
    left.averageRgb[2] - right.averageRgb[2],
  )
  const colorSimilarity = Math.max(0, 1 - colorDistance / 441.67295593)
  const score = histogramSimilarity * 0.72 + colorSimilarity * 0.28
  return clampScore(score)
}

export function recognizeGarments(
  photoSignature: ImageSignature,
  garments: Garment[],
): RecognitionSlot[] {
  return categoryOrder.map((category) => {
    const alternatives = garments
      .filter((garment) => garment.category === category && !garment.archivedAt && garment.signature)
      .map((garment) => ({
        garmentId: garment.id,
        garmentName: garment.name,
        category,
        confidence: compareSignatures(photoSignature, garment.signature!),
      }))
      .sort((left, right) => right.confidence - left.confidence)
      .slice(0, 3)

    const best = alternatives[0]
    return {
      category,
      selectedGarmentId: best?.confidence && best.confidence >= 0.42 ? best.garmentId : undefined,
      confidence: best?.confidence ?? 0,
      alternatives,
    }
  })
}

export async function fileToDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result))
    reader.onerror = () => reject(reader.error ?? new Error('图片读取失败'))
    reader.readAsDataURL(file)
  })
}

export function resizeImageDataUrl(dataUrl: string, maxSide = 1280, quality = 0.86): Promise<string> {
  return loadImage(dataUrl).then((image) => {
    const scale = Math.min(1, maxSide / Math.max(image.width, image.height))
    const width = Math.max(1, Math.round(image.width * scale))
    const height = Math.max(1, Math.round(image.height * scale))
    const canvas = document.createElement('canvas')
    canvas.width = width
    canvas.height = height
    const context = canvas.getContext('2d')
    if (!context) {
      throw new Error('无法处理图片')
    }
    context.drawImage(image, 0, 0, width, height)
    return canvas.toDataURL('image/jpeg', quality)
  })
}

function colorBucket(red: number, green: number, blue: number): number {
  const r = Math.min(CHANNEL_BINS - 1, Math.floor((red / 256) * CHANNEL_BINS))
  const g = Math.min(CHANNEL_BINS - 1, Math.floor((green / 256) * CHANNEL_BINS))
  const b = Math.min(CHANNEL_BINS - 1, Math.floor((blue / 256) * CHANNEL_BINS))
  return r * CHANNEL_BINS * CHANNEL_BINS + g * CHANNEL_BINS + b
}

function clampScore(value: number): number {
  return Math.max(0, Math.min(1, Number(value.toFixed(4))))
}

function loadImage(dataUrl: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image()
    image.onload = () => resolve(image)
    image.onerror = () => reject(new Error('图片加载失败'))
    image.src = dataUrl
  })
}
