import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import ReportObjectsList from './ReportObjectsList.jsx'

describe('ReportObjectsList', () => {
  it('renders nothing when objects empty or missing', () => {
    const { container } = render(<ReportObjectsList objects={[]} />)
    expect(container.firstChild).toBeNull()
    const { container: c2 } = render(<ReportObjectsList />)
    expect(c2.firstChild).toBeNull()
  })

  it('renders issues and measurement chips with warn state', () => {
    const objects = [
      {
        objectType: 'RAMP',
        issues: ['TOO_STEEP'],
        measurements: { slope_percent: '15', width_cm: '120' },
      },
    ]
    render(<ReportObjectsList objects={objects} />)

    expect(screen.getByText(/objects/i)).toBeInTheDocument()
    expect(screen.getByText(/too steep/i)).toBeInTheDocument()
    expect(screen.getByText(/slope/i)).toBeInTheDocument()
    expect(screen.getByText(/Slope:\s*15/)).toBeInTheDocument()
    expect(screen.getByText(/≤10 ok/i)).toBeInTheDocument()
    expect(screen.getByText(/≥100 ok/i)).toBeInTheDocument()
  })

  it('falls back labels when object type is unknown', () => {
    render(
      <ReportObjectsList
        objects={[
          {
            objectType: 'UNKNOWN_TYPE',
            issues: ['SOME_ISSUE'],
            measurements: { custom_key: '1' },
          },
        ]}
      />,
    )
    expect(screen.getByText('UNKNOWN_TYPE')).toBeInTheDocument()
    expect(screen.getByText(/some issue/i)).toBeInTheDocument()
    expect(screen.getByText(/custom_key/i)).toBeInTheDocument()
  })
})
