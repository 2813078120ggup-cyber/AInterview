import Editor, { loader } from '@monaco-editor/react'
import * as monaco from 'monaco-editor'
import editorWorker from '../../../node_modules/monaco-editor/esm/vs/editor/editor.worker.js?worker'

self.MonacoEnvironment = { getWorker: () => new editorWorker() }
loader.config({ monaco })

export type CodeEditorProps = {
  value: string
  onChange: (value: string) => void
  height?: number | string
  readOnly?: boolean
  dark?: boolean
}

export function CodeEditor({ value, onChange, height = 340, readOnly = false, dark = false }: CodeEditorProps) {
  return (
    <Editor
      height={height}
      language="java"
      value={value}
      onChange={next => onChange(next ?? '')}
      theme={dark ? 'vs-dark' : 'vs'}
      options={{
        minimap: { enabled: false },
        fontSize: 14,
        scrollBeyondLastLine: false,
        automaticLayout: true,
        readOnly,
        tabSize: 4,
        wordWrap: 'on',
      }}
    />
  )
}
