import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import './styles.css'
import { App } from './app'
import { AuthSessionProvider } from './components/auth-session-provider'
import { initializeTheme } from './lib/theme'
import { PlatformUiSettingsProvider } from './lib/platform-ui-settings-context'
initializeTheme()
createRoot(document.getElementById('root')!).render(<StrictMode><BrowserRouter><AuthSessionProvider><PlatformUiSettingsProvider><App /></PlatformUiSettingsProvider></AuthSessionProvider></BrowserRouter></StrictMode>)
