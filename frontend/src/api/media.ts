import { i18n } from '@/i18n'
import { api } from './http'
import { problemMessage } from './problem'

/** Photo types the backend accepts, for `<input accept>` and checking a file before asking for an upload URL. */
export const ACCEPTED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']
export const MAX_IMAGE_MB = 10

/**
 * Sends a photo or video straight to object storage and returns its media id, ready to pass to the
 * endpoint that uses it (e.g. `PUT /api/staff/me/photo`). Throws an `Error` whose message is ready to show.
 */
export async function uploadMedia(file: File): Promise<string> {
  const { data, error, response } = await api.POST('/api/media/uploads', {
    body: { contentType: file.type, sizeBytes: file.size },
  })
  if (error) throw new Error(problemMessage(error, response))

  // no Authorization header: the presigned URL carries its own signature
  const upload = await fetch(data.uploadUrl, {
    method: 'PUT',
    body: file,
    headers: { 'Content-Type': file.type },
  }).catch(() => undefined)
  if (!upload?.ok) throw new Error(i18n.global.t('media.uploadFailed'))
  return data.mediaId
}
