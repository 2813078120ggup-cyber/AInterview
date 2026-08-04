/**
 * Minimal typing for the browser's SpeechRecognition API (Chrome/Edge),
 * which is not part of the standard lib.dom typings yet.
 */
export type SpeechRecognitionResultLike = ArrayLike<{ transcript?: string }> & {
  isFinal?: boolean
}

export type SpeechRecognitionEventLike = {
  resultIndex: number
  results: ArrayLike<SpeechRecognitionResultLike>
}

export type BrowserSpeechRecognition = {
  lang: string
  interimResults: boolean
  continuous: boolean
  onresult: ((event: SpeechRecognitionEventLike) => void) | null
  onerror: (() => void) | null
  onend: (() => void) | null
  start: () => void
  abort?: () => void
  stop: () => void
}

export function browserSpeechRecognitionCtor(): (new () => BrowserSpeechRecognition) | undefined {
  const speechWindow = window as unknown as {
    SpeechRecognition?: new () => BrowserSpeechRecognition
    webkitSpeechRecognition?: new () => BrowserSpeechRecognition
  }
  return speechWindow.SpeechRecognition || speechWindow.webkitSpeechRecognition
}
