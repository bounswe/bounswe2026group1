import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import MapFilters from './MapFilters.jsx'

describe('MapFilters', () => {
  let onChange, onClose, user

  beforeEach(() => {
    onChange = vi.fn()
    onClose = vi.fn()
    user = userEvent.setup()
  })

  function renderFilters(props = {}) {
    return render(
      <MapFilters
        excludedTypes={new Set()}
        excludedIssues={new Set()}
        onChange={onChange}
        onClose={onClose}
        {...props}
      />,
    )
  }

  it('renders one row per object type with a checked-by-default checkbox', () => {
    renderFilters()
    expect(screen.getByLabelText('Show Ramp')).toBeChecked()
    expect(screen.getByLabelText('Show Stair')).toBeChecked()
  })

  it('toggling the type checkbox off adds the type to excludedTypes', async () => {
    renderFilters()
    await user.click(screen.getByLabelText('Show Ramp'))
    expect(onChange).toHaveBeenCalledTimes(1)
    const [nextTypes, nextIssues] = onChange.mock.calls[0]
    expect(nextTypes.has('RAMP')).toBe(true)
    expect(nextIssues.size).toBe(0)
  })

  it('expanding a type reveals per-issue checkboxes', async () => {
    renderFilters()
    await user.click(screen.getByLabelText('Expand Ramp'))
    expect(screen.getByLabelText('Show Ramp Missing')).toBeInTheDocument()
  })

  it('toggling an issue off adds the namespaced token to excludedIssues', async () => {
    renderFilters()
    await user.click(screen.getByLabelText('Expand Ramp'))
    await user.click(screen.getByLabelText('Show Ramp Missing'))
    const [nextTypes, nextIssues] = onChange.mock.calls.at(-1)
    expect(nextTypes.size).toBe(0)
    expect(nextIssues.has('RAMP:MISSING')).toBe(true)
  })

  it('shows the active hidden count and a Show all button when filters are set', async () => {
    renderFilters({ excludedTypes: new Set(['RAMP']), excludedIssues: new Set(['STAIR:CRACKED']) })
    expect(screen.getByText(/2 hidden/i)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /show all/i }))
    const [nextTypes, nextIssues] = onChange.mock.calls.at(-1)
    expect(nextTypes.size).toBe(0)
    expect(nextIssues.size).toBe(0)
  })

  it('hiding the whole type drops any per-issue exclusions for it', async () => {
    renderFilters({
      excludedTypes: new Set(),
      excludedIssues: new Set(['RAMP:MISSING', 'STAIR:CRACKED']),
    })
    await user.click(screen.getByLabelText('Show Ramp'))
    const [nextTypes, nextIssues] = onChange.mock.calls.at(-1)
    expect(nextTypes.has('RAMP')).toBe(true)
    expect([...nextIssues]).toEqual(['STAIR:CRACKED'])
  })

  it('Escape calls onClose', async () => {
    renderFilters()
    await user.keyboard('{Escape}')
    expect(onClose).toHaveBeenCalled()
  })
})
