import { describe, it, expect } from 'vitest'
import { isVideoUrl } from './mediaType.js'

describe('isVideoUrl', () => {
  it('returns true for .mp4 / .mov / .webm', () => {
    expect(isVideoUrl('https://cdn/foo.mp4')).toBe(true)
    expect(isVideoUrl('https://cdn/foo.MOV')).toBe(true)
    expect(isVideoUrl('https://cdn/foo.webm')).toBe(true)
  })

  it('ignores S3 query strings', () => {
    expect(isVideoUrl('https://cdn/foo.mp4?X-Amz-Sig=abc')).toBe(true)
  })

  it('returns false for images and missing values', () => {
    expect(isVideoUrl('https://cdn/foo.jpg')).toBe(false)
    expect(isVideoUrl('https://cdn/foo.png')).toBe(false)
    expect(isVideoUrl(null)).toBe(false)
    expect(isVideoUrl(undefined)).toBe(false)
    expect(isVideoUrl('')).toBe(false)
  })
})
