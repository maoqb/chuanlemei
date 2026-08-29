import type { Garment } from './types'
import { compareSignatures, createImageSignatureFromPixels, recognizeGarments } from './vision'

describe('vision helpers', () => {
  it('scores identical colors higher than different colors', () => {
    const red = solidSignature(220, 34, 34)
    const redAgain = solidSignature(220, 34, 34)
    const blue = solidSignature(32, 78, 180)

    expect(compareSignatures(red, redAgain)).toBeGreaterThan(0.99)
    expect(compareSignatures(red, blue)).toBeLessThan(0.45)
  })

  it('recognizes the best garment per category from saved signatures', () => {
    const red = solidSignature(220, 34, 34)
    const blue = solidSignature(32, 78, 180)
    const garments: Garment[] = [
      garment('red-top', '红衬衫', 'top', red),
      garment('blue-top', '蓝衬衫', 'top', blue),
      garment('blue-shoes', '蓝鞋', 'shoes', blue),
    ]

    const slots = recognizeGarments(red, garments)
    expect(slots.find((slot) => slot.category === 'top')?.selectedGarmentId).toBe('red-top')
    expect(slots.find((slot) => slot.category === 'shoes')?.selectedGarmentId).toBeUndefined()
  })
})

function solidSignature(red: number, green: number, blue: number) {
  const pixels = new Uint8ClampedArray(4 * 8 * 8)
  for (let index = 0; index < pixels.length; index += 4) {
    pixels[index] = red
    pixels[index + 1] = green
    pixels[index + 2] = blue
    pixels[index + 3] = 255
  }
  return createImageSignatureFromPixels(pixels, 8, 8)
}

function garment(id: string, name: string, category: Garment['category'], signature: Garment['signature']): Garment {
  return {
    id,
    name,
    category,
    color: '#000000',
    signature,
    createdAt: '2026-08-29T00:00:00.000Z',
    updatedAt: '2026-08-29T00:00:00.000Z',
  }
}
