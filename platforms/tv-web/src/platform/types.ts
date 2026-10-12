export interface PlatformAdapter {
  readonly name: 'webos' | 'browser'
  /** Keeps the TV's screensaver from covering the app while it is on screen. Called once, at startup. */
  keepScreenOn(): void
}
