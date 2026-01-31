import axios from 'axios'

const baseURL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

export const api = axios.create({
  baseURL,
})

/**
 * 上傳音檔並取得 Word 檔（blob）
 * @param {File} file - 音檔
 * @returns {Promise<Blob>}
 */
export async function generateMeetingMinutes(file) {
  const formData = new FormData()
  formData.append('file', file)

  const response = await api.post('/api/generate', formData, {
    responseType: 'blob',
  })

  return response.data
}
