import { describe, it, expect, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ThemeProvider, useTheme } from './ThemeContext.jsx'

function Probe() {
  const { mode, setMode } = useTheme()
  return (
    <div>
      <span data-testid="mode">{mode}</span>
      <button type="button" onClick={() => setMode('dark')}>
        go dark
      </button>
    </div>
  )
}

describe('ThemeContext', () => {
  beforeEach(() => {
    localStorage.removeItem('theme_mode')
  })

  it('provides mode and persists setMode', async () => {
    const user = userEvent.setup()
    render(
      <ThemeProvider>
        <Probe />
      </ThemeProvider>,
    )

    expect(screen.getByTestId('mode')).toHaveTextContent('system')
    await user.click(screen.getByRole('button', { name: /go dark/i }))
    expect(screen.getByTestId('mode')).toHaveTextContent('dark')
    expect(localStorage.getItem('theme_mode')).toBe('dark')
  })

  it('throws useTheme outside provider', () => {
    expect(() => render(<Probe />)).toThrow(/useTheme must be used within a ThemeProvider/)
  })
})
