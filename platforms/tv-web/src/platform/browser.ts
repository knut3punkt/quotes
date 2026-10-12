import type { PlatformAdapter } from './types'

// A browser has no TV screensaver to hold off.
export const browserAdapter: PlatformAdapter = { name: 'browser', keepScreenOn: () => {} }
