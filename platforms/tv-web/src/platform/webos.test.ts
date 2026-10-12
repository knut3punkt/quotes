import { describe, expect, it } from 'vitest'
import { screenSaverResponse } from './webos'

describe('screenSaverResponse', () => {
  const request = JSON.stringify({ returnValue: true, state: 'Active', timestamp: 1234, instantBoot: 'off' })

  it('vetoes an announced screensaver with its timestamp while the app is visible', () => {
    expect(screenSaverResponse(request, true)).toEqual({
      clientName: 'no.esotericgames.quotes',
      ack: false,
      timestamp: 1234,
    })
  })

  it('lets the screensaver start while the app is hidden', () => {
    expect(screenSaverResponse(request, false)).toBeNull()
  })

  it('ignores the subscription reply and other states', () => {
    expect(screenSaverResponse(JSON.stringify({ returnValue: true, subscribed: true }), true)).toBeNull()
    expect(screenSaverResponse(JSON.stringify({ state: 'Inactive', timestamp: 1 }), true)).toBeNull()
  })

  it('ignores malformed messages', () => {
    expect(screenSaverResponse('not json', true)).toBeNull()
    expect(screenSaverResponse('null', true)).toBeNull()
  })
})
