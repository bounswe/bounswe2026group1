import { render, screen } from '@testing-library/react'
import DataTable from './DataTable.jsx'

const cols = [
  { key: 'name', label: 'Name' },
  { key: 'role', label: 'Role', render: (row) => <strong>{row.role}</strong> },
]

describe('DataTable', () => {
  it('shows loading row', () => {
    render(<DataTable columns={cols} rows={[]} getRowKey={(r) => r.id} isLoading />)
    expect(screen.getByText(/loading/i)).toBeInTheDocument()
  })

  it('shows error message', () => {
    render(
      <DataTable
        columns={cols}
        rows={[]}
        getRowKey={(r) => r.id}
        isError
        error={new Error('boom')}
      />,
    )
    expect(screen.getByText('boom')).toBeInTheDocument()
  })

  it('shows empty label', () => {
    render(<DataTable columns={cols} rows={[]} getRowKey={(r) => r.id} emptyLabel="Nothing" />)
    expect(screen.getByText('Nothing')).toBeInTheDocument()
  })

  it('renders rows and custom render', () => {
    render(
      <DataTable
        columns={cols}
        rows={[{ id: 1, name: 'Ada', role: 'ADMIN' }]}
        getRowKey={(r) => r.id}
      />,
    )
    expect(screen.getByText('Ada')).toBeInTheDocument()
    expect(screen.getByText('ADMIN')).toBeInTheDocument()
  })
})
