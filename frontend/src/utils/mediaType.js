// S3 returns plain URLs ending in the original filename, so the extension is
// the cheapest way to know whether a media URL points at a video. Backend's
// allowlist (S3MediaService.ALLOWED_CONTENT_TYPES) is mp4 + quicktime today;
// webm covers anything we add later.
const VIDEO_EXTENSIONS = ['.mp4', '.mov', '.webm']

export function isVideoUrl(url) {
  if (!url) return false
  const path = url.toLowerCase().split('?')[0]
  return VIDEO_EXTENSIONS.some((ext) => path.endsWith(ext))
}
