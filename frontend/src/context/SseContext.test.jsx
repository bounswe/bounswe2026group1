import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SseProvider, useSseStatus, useSseStatusSetter } from './SseContext.jsx'

function Probe() {
  const status = useSseStatus()
  const setStatus = useSseStatusSetter()
  return (
    <div>
      <span data-testid="s">{status}</span>
      <button type="button" onClick={() => setStatus('connected')}>
        connect
      </button>
    </div>
  )
}

describe('SseContext', () => {
  it('defaults to disconnected and updates via setter', async () => {
    const user = userEvent.setup()
    render(
      <SseProvider>
        <Probe />
      </SseProvider>,
    )
    expect(screen.getByTestId('s')).toHaveTextContent('disconnected')
    await user.click(screen.getByRole('button', { name: /connect/i }))
    expect(screen.getByTestId('s')).toHaveTextContent('connected')
  })
})
