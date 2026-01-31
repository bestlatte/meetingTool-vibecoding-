import { useState, useRef } from 'react'
import { generateMeetingMinutes } from './lib/api'

const DOWNLOAD_FILENAME = 'meeting_minutes.docx'

function App() {
  const [file, setFile] = useState(null)
  const [isDragging, setIsDragging] = useState(false)
  const [isLoading, setIsLoading] = useState(false)
  const inputRef = useRef(null)

  const baseURL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

  const handleDragOver = (e) => {
    e.preventDefault()
    e.stopPropagation()
    setIsDragging(true)
  }

  const handleDragLeave = (e) => {
    e.preventDefault()
    e.stopPropagation()
    setIsDragging(false)
  }

  const handleDrop = (e) => {
    e.preventDefault()
    e.stopPropagation()
    setIsDragging(false)
    if (isLoading) return
    const dropped = e.dataTransfer.files[0]
    if (dropped && (dropped.type.startsWith('audio/') || dropped.type === 'video/mp4')) {
      setFile(dropped)
    }
  }

  const handleFileChange = (e) => {
    if (isLoading) return
    const selected = e.target.files[0]
    if (selected) setFile(selected)
  }

  const handleRemove = () => {
    if (isLoading) return
    setFile(null)
    if (inputRef.current) inputRef.current.value = ''
  }

  const triggerDownload = (blob) => {
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = DOWNLOAD_FILENAME
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  }

  const handleSubmit = async () => {
    if (!file) {
      alert('請先選擇音檔')
      return
    }

    setIsLoading(true)

    try {
      const blob = await generateMeetingMinutes(file)
      triggerDownload(blob)
    } catch (err) {
      if (err.response && err.response.data instanceof Blob) {
        try {
          const text = await err.response.data.text()
          const json = JSON.parse(text)
          alert(json.message || '處理失敗，請稍後再試')
        } catch {
          alert('處理失敗，請稍後再試')
        }
      } else {
        alert(err.response?.data?.message || err.message || '處理失敗，請稍後再試')
      }
    } finally {
      setIsLoading(false)
    }
  }

  const disabled = isLoading
  const uploadAreaDisabled = disabled

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-50 to-slate-100 flex items-center justify-center p-4">
      <div className="w-full max-w-lg">
        <div className="bg-white rounded-2xl shadow-xl border border-slate-200/60 overflow-hidden">
          <div className="px-6 pt-6 pb-2">
            <h1 className="text-2xl font-bold text-slate-800 text-center">
              🎙️ AI 會議記錄助手
            </h1>
          </div>

          <div className="px-6 pb-6 space-y-5">
            {/* 檔案上傳區 */}
            <div>
              <label className="block text-sm font-medium text-slate-600 mb-2">
                上傳會議音檔
              </label>
              <input
                ref={inputRef}
                type="file"
                accept="audio/*,video/mp4"
                onChange={handleFileChange}
                className="hidden"
                disabled={uploadAreaDisabled}
              />
              <div
                onDragOver={handleDragOver}
                onDragLeave={handleDragLeave}
                onDrop={handleDrop}
                onClick={() => !uploadAreaDisabled && inputRef.current?.click()}
                className={`
                  border-2 border-dashed rounded-xl p-6 text-center cursor-pointer transition-colors
                  ${isDragging ? 'border-indigo-400 bg-indigo-50/50' : 'border-slate-300 hover:border-slate-400'}
                  ${uploadAreaDisabled ? 'pointer-events-none opacity-60' : ''}
                `}
              >
                {file ? (
                  <div className="flex items-center justify-center gap-3 flex-wrap">
                    <span className="text-slate-700 font-medium truncate max-w-[200px]">
                      {file.name}
                    </span>
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation()
                        handleRemove()
                      }}
                      className="px-3 py-1 text-sm font-medium text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                    >
                      移除
                    </button>
                  </div>
                ) : (
                  <p className="text-slate-500">
                    拖曳音檔到這裡，或點擊選擇檔案
                  </p>
                )}
              </div>
            </div>

            {/* 功能按鈕 */}
            <button
              type="button"
              onClick={handleSubmit}
              disabled={disabled}
              className="w-full py-3 px-4 rounded-xl font-semibold text-white bg-indigo-600 hover:bg-indigo-700 disabled:opacity-60 disabled:cursor-not-allowed transition-all flex items-center justify-center gap-2"
            >
              {isLoading ? (
                <>
                  <svg
                    className="animate-spin h-5 w-5"
                    xmlns="http://www.w3.org/2000/svg"
                    fill="none"
                    viewBox="0 0 24 24"
                  >
                    <circle
                      className="opacity-25"
                      cx="12"
                      cy="12"
                      r="10"
                      stroke="currentColor"
                      strokeWidth="4"
                    />
                    <path
                      className="opacity-75"
                      fill="currentColor"
                      d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
                    />
                  </svg>
                  AI 正在聆聽中...
                </>
              ) : (
                '✨ 開始整理'
              )}
            </button>
          </div>
        </div>

        {baseURL && (
          <p className="text-center text-xs text-slate-400 mt-3">
            API: {baseURL}
          </p>
        )}
      </div>
    </div>
  )
}

export default App
