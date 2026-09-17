import { browserAdapter } from './browser'
import { webosAdapter } from './webos'
import type { PlatformAdapter } from './types'

function isRunningOnWebOS(): boolean {
  return typeof window !== 'undefined' && ('PalmSystem' in window || /Web0S/i.test(navigator.userAgent))
}

export const platform: PlatformAdapter = isRunningOnWebOS() ? webosAdapter : browserAdapter
