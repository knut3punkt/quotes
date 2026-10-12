import type { PlatformAdapter } from './types'

/**
 * webOS has no public API for this, so this uses the TV power service the way Kodi and RetroArch do: subscribe to
 * screensaver requests, and veto each one with `ack: false` and its timestamp. The veto only applies while the app is
 * visible, so the screensaver still comes on as usual while another app is in front.
 */
const REGISTER_URI = 'luna://com.webos.service.tvpower/power/registerScreenSaverRequest'
const RESPONSE_URI = 'luna://com.webos.service.tvpower/power/responseScreenSaverRequest'
const CLIENT_NAME = 'no.esotericgames.quotes'

/** The webOS web runtime's bridge to system services. One bridge carries one call (or subscription) at a time. */
interface ServiceBridge {
  onservicecallback: ((message: string) => void) | null
  call(uri: string, parameters: string): void
}
type ServiceBridgeConstructor = new () => ServiceBridge

function bridgeConstructor(): ServiceBridgeConstructor | null {
  const globals = window as unknown as Record<string, ServiceBridgeConstructor | undefined>
  return globals.WebOSServiceBridge ?? globals.PalmServiceBridge ?? null
}

/** The answer to a screensaver request, or null to let the screensaver start. */
export function screenSaverResponse(
  message: string,
  visible: boolean,
): { clientName: string; ack: false; timestamp: unknown } | null {
  if (!visible) return null
  let parsed: unknown
  try {
    parsed = JSON.parse(message)
  } catch {
    return null
  }
  if (typeof parsed !== 'object' || parsed === null) return null
  const { state, timestamp } = parsed as { state?: unknown; timestamp?: unknown }
  if (state !== 'Active' || timestamp === undefined) return null
  return { clientName: CLIENT_NAME, ack: false, timestamp }
}

function keepScreenOn() {
  const Bridge = bridgeConstructor()
  if (!Bridge) {
    console.warn('No webOS service bridge; the screensaver will come on as usual.')
    return
  }
  // Answers go on a bridge of their own, so they never replace the subscription.
  const subscription = new Bridge()
  const answers = new Bridge()
  subscription.onservicecallback = (message) => {
    if (message.includes('"returnValue":false')) console.warn(`The TV refused the screensaver veto: ${message}`)
    const response = screenSaverResponse(message, document.visibilityState === 'visible')
    if (response) answers.call(RESPONSE_URI, JSON.stringify(response))
  }
  subscription.call(REGISTER_URI, JSON.stringify({ subscribe: true, clientName: CLIENT_NAME }))
}

export const webosAdapter: PlatformAdapter = { name: 'webos', keepScreenOn }
