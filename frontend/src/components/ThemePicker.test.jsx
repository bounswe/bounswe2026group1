import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ThemeProvider } from '../context/ThemeContext.jsx'
import ThemeToggleButton, { ThemePickerSegmented } from './ThemePicker.jsx'

function renderSegmented() {
  return render(
    <ThemeProvider>
      <ThemePickerSegmented />
    </ThemeProvider>,
  )
}

function renderToggle() {
  return render(
    <ThemeProvider>
      <ThemeToggleButton />
    </ThemeProvider>,
  )
}

describe('ThemePicker', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  describe('ThemePickerSegmented', () => {
    it('renders Appearance group with System, Light, and Dark options', () => {
      renderSegmented()
      expect(screen.getByRole('group', { name: /theme/i })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /system/i })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /light/i })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /dark/i })).toBeInTheDocument()
    })

    it('sets theme mode in localStorage when Light is selected', async () => {
      const user = userEvent.setup()
      renderSegmented()
      await user.click(screen.getByRole('button', { name: /light/i }))
      expect(localStorage.getItem('theme_mode')).toBe('light')
    })

    it('marks the active segment with aria-pressed', async () => {
      const user = userEvent.setup()
      renderSegmented()
      const darkBtn = screen.getByRole('button', { name: /dark/i })
      await user.click(darkBtn)
      expect(darkBtn).toHaveAttribute('aria-pressed', 'true')
    })
  })

  describe('ThemeToggleButton', () => {
    it('cycles theme mode on click', async () => {
      const user = userEvent.setup()
      renderToggle()
      const btn = screen.getByRole('button', { name: /theme:/i })
      await user.click(btn)
      expect(localStorage.getItem('theme_mode')).toBe('light')
      await user.click(btn)
      expect(localStorage.getItem('theme_mode')).toBe('dark')
    })
  })
})
