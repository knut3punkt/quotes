import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { platform } from './platform'

// A quote screen is meant to be left on, so the TV's screensaver shouldn't take over.
platform.keepScreenOn()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
