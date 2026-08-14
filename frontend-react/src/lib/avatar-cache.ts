const avatarCacheVersion = 'v1'
const avatarThumbnailSize = 128
const maxCachedDataUrlLength = 256_000

function cacheKey(userId: string) {
  return `ai-interview-avatar:${avatarCacheVersion}:${userId}`
}

export function readCachedAvatar(userId: string | null | undefined) {
  if (!userId) return null
  try {
    const value = localStorage.getItem(cacheKey(userId))
    return value?.startsWith('data:image/') ? value : null
  } catch {
    return null
  }
}

export function removeCachedAvatar(userId: string | null | undefined) {
  if (!userId) return
  try {
    localStorage.removeItem(cacheKey(userId))
  } catch {
    // Browsers may disable local storage; the remote avatar remains available.
  }
}

export async function cacheAvatarBlob(userId: string, blob: Blob) {
  if (!blob.type.startsWith('image/')) return
  let objectUrl: string | null = null
  let bitmap: ImageBitmap | null = null
  try {
    let source: CanvasImageSource
    let width: number
    let height: number
    if ('createImageBitmap' in window) {
      bitmap = await createImageBitmap(blob)
      source = bitmap
      width = bitmap.width
      height = bitmap.height
    } else {
      objectUrl = URL.createObjectURL(blob)
      const image = new Image()
      image.src = objectUrl
      await image.decode()
      source = image
      width = image.naturalWidth
      height = image.naturalHeight
    }
    const scale = Math.min(1, avatarThumbnailSize / Math.max(width, height))
    const canvas = document.createElement('canvas')
    canvas.width = Math.max(1, Math.round(width * scale))
    canvas.height = Math.max(1, Math.round(height * scale))
    const context = canvas.getContext('2d')
    if (!context) return
    context.drawImage(source, 0, 0, canvas.width, canvas.height)
    const dataUrl = canvas.toDataURL('image/webp', 0.86)
    if (dataUrl.length <= maxCachedDataUrlLength) localStorage.setItem(cacheKey(userId), dataUrl)
  } catch {
    // Cache creation is an optional visual optimization and must not block avatar display.
  } finally {
    bitmap?.close()
    if (objectUrl) URL.revokeObjectURL(objectUrl)
  }
}
