import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import Pagination from './Pagination.jsx'

describe('Pagination', () => {
  it('shows current page and total pages', () => {
    const { container } = render(
      <Pagination page={2} totalPages={5} onPageChange={() => {}} />,
    )
    expect(container.textContent).toMatch(/Page\s+3\s+of\s+5/)
  })

  it('calls onPageChange with previous index when Previous is clicked', async () => {
    const user = userEvent.setup()
    const onPageChange = vi.fn()
    render(<Pagination page={1} totalPages={5} onPageChange={onPageChange} />)
    await user.click(screen.getByRole('button', { name: /previous/i }))
    expect(onPageChange).toHaveBeenCalledWith(0)
  })

  it('calls onPageChange with next index when Next is clicked', async () => {
    const user = userEvent.setup()
    const onPageChange = vi.fn()
    render(<Pagination page={1} totalPages={5} onPageChange={onPageChange} />)
    await user.click(screen.getByRole('button', { name: /next/i }))
    expect(onPageChange).toHaveBeenCalledWith(2)
  })

  it('disables Previous on the first page', () => {
    render(<Pagination page={0} totalPages={3} onPageChange={() => {}} />)
    expect(screen.getByRole('button', { name: /previous/i })).toBeDisabled()
  })

  it('disables Next on the last page', () => {
    render(<Pagination page={2} totalPages={3} onPageChange={() => {}} />)
    expect(screen.getByRole('button', { name: /next/i })).toBeDisabled()
  })

  it('treats zero or missing totalPages as one page', () => {
    const { container } = render(
      <Pagination page={0} totalPages={0} onPageChange={() => {}} />,
    )
    expect(container.textContent).toMatch(/Page\s+1\s+of\s+1/)
    expect(screen.getByRole('button', { name: /next/i })).toBeDisabled()
  })
})
