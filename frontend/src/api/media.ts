import { api } from './http'

/**
 * Sends a photo or video straight to object storage and returns its media id, ready to pass to the
 * endpoint that uses it (e.g. `PUT /api/staff/me/photo`).
 */
export async function uploadMedia(file: File): Promise<string> {
  const { data, error } = await api.POST('/api/media/uploads', {
    body: { contentType: file.type, sizeBytes: file.size },
  })
  if (error) throw new Error(error.detail ?? 'Upload refused')

  // no Authorization header: the presigned URL carries its own signature
  const response = await fetch(data.uploadUrl, {
    method: 'PUT',
    body: file,
    headers: { 'Content-Type': file.type },
  })
  if (!response.ok) throw new Error(`Upload failed (${response.status})`)
  return data.mediaId
}
