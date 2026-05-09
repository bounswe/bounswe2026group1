import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import RoutingPreferencesSection from './RoutingPreferencesSection.jsx'

vi.mock('../hooks/useRoutingPreferences.js', () => ({
  useRoutingPreferences: vi.fn(),
  useUpdateRoutingPreferences: vi.fn(),
  useCreateCustomProfile: vi.fn(),
  useUpdateCustomProfile: vi.fn(),
  useDeleteCustomProfile: vi.fn(),
  useActivateCustomProfile: vi.fn(),
}))

import {
  useActivateCustomProfile,
  useCreateCustomProfile,
  useDeleteCustomProfile,
  useRoutingPreferences,
  useUpdateCustomProfile,
  useUpdateRoutingPreferences,
} from '../hooks/useRoutingPreferences.js'

// Trimmed catalog mirroring what GET /routing-preferences returns. Real
// catalogue is bigger; tests only need a representative slice.
const SAMPLE_AVAILABLE_PRESETS = [
  { name: 'NONE',                label: 'No preferences',                 constraints: [],                                 defaultTravelMode: null },
  { name: 'WHEELCHAIR_USER',     label: 'Wheelchair user',                constraints: ['AVOID_STAIRS', 'REQUIRE_RAMPS'], defaultTravelMode: 'WHEELCHAIR' },
  { name: 'BLIND_OR_LOW_VISION', label: 'Blind or low vision',            constraints: ['AVOID_LOW_CLEARANCE'],            defaultTravelMode: 'WALKING' },
  { name: 'MOBILITY_LIMITED',    label: 'Mobility limited (cane, walker)', constraints: ['REQUIRE_HANDRAILS'],             defaultTravelMode: 'WALKING' },
  { name: 'CUSTOM',              label: 'Custom',                         constraints: [],                                 defaultTravelMode: null },
]

const SAMPLE_AVAILABLE_CONSTRAINTS = [
  { name: 'AVOID_STAIRS',     label: 'Avoid stairs entirely',         description: 'Routes will avoid stairs.' },
  { name: 'REQUIRE_RAMPS',    label: 'Prefer routes with ramps',      description: 'Treats missing ramps as obstacles.' },
  { name: 'REQUIRE_HANDRAILS',label: 'Require handrails',             description: 'Avoids stairs/ramps without handrails.' },
  { name: 'AVOID_LOW_CLEARANCE', label: 'Avoid low overhead clearance', description: 'Avoids low-hanging obstacles.' },
]

function baseData(overrides = {}) {
  return {
    preferredPreset: 'NONE',
    constraints: [],
    preferredTravelMode: null,
    activeCustomProfileId: null,
    availablePresets: SAMPLE_AVAILABLE_PRESETS,
    availableConstraints: SAMPLE_AVAILABLE_CONSTRAINTS,
    customProfiles: [],
    ...overrides,
  }
}

describe('RoutingPreferencesSection', () => {
  const updatePrefsMutate = vi.fn()
  const updatePrefsMutateAsync = vi.fn()
  const createMutateAsync = vi.fn()
  const updateMutateAsync = vi.fn()
  const deleteMutateAsync = vi.fn()
  const activateMutate = vi.fn()
  const refetch = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
    useRoutingPreferences.mockReturnValue({
      data: baseData(),
      isPending: false,
      isError: false,
      error: null,
      refetch,
    })
    useUpdateRoutingPreferences.mockReturnValue({
      mutate: updatePrefsMutate,
      mutateAsync: updatePrefsMutateAsync,
      isPending: false,
    })
    useCreateCustomProfile.mockReturnValue({ mutateAsync: createMutateAsync, isPending: false })
    useUpdateCustomProfile.mockReturnValue({ mutateAsync: updateMutateAsync, isPending: false })
    useDeleteCustomProfile.mockReturnValue({ mutateAsync: deleteMutateAsync, isPending: false })
    useActivateCustomProfile.mockReturnValue({
      mutate: activateMutate,
      mutateAsync: vi.fn(),
      isPending: false,
    })
  })

  it('renders a loading state while the query is pending', () => {
    useRoutingPreferences.mockReturnValue({
      data: undefined,
      isPending: true,
      isError: false,
      error: null,
      refetch,
    })
    render(<RoutingPreferencesSection />)
    expect(screen.getByText(/loading routing preferences/i)).toBeInTheDocument()
  })

  it('hides the CUSTOM placeholder preset from the catalog grid', () => {
    render(<RoutingPreferencesSection />)
    // The pseudo-preset CUSTOM is server-side bookkeeping; users activate it
    // by saving a custom profile, never by clicking it directly.
    expect(screen.queryByRole('button', { name: /^custom$/i })).not.toBeInTheDocument()
  })

  it('marks the matching built-in preset as Active', () => {
    useRoutingPreferences.mockReturnValue({
      data: baseData({ preferredPreset: 'WHEELCHAIR_USER' }),
      isPending: false,
      isError: false,
      refetch,
    })
    render(<RoutingPreferencesSection />)
    const card = screen.getByRole('button', { name: /^activate wheelchair user$/i })
    expect(within(card).getByText(/^active$/i)).toBeInTheDocument()
  })

  it('selecting a built-in preset triggers updateRoutingPreferences', async () => {
    const user = userEvent.setup()
    render(<RoutingPreferencesSection />)
    await user.click(screen.getByRole('button', { name: /^activate wheelchair user$/i }))
    expect(updatePrefsMutate).toHaveBeenCalledWith({ preferredPreset: 'WHEELCHAIR_USER' })
  })

  it('activates a custom profile when its card is clicked', async () => {
    const user = userEvent.setup()
    useRoutingPreferences.mockReturnValue({
      data: baseData({
        customProfiles: [
          { id: 11, name: 'Work commute', constraints: ['AVOID_STAIRS'], preferredTravelMode: 'WHEELCHAIR' },
        ],
      }),
      isPending: false,
      isError: false,
      refetch,
    })
    render(<RoutingPreferencesSection />)
    await user.click(screen.getByRole('button', { name: /^activate work commute$/i }))
    expect(activateMutate).toHaveBeenCalledWith(11)
  })

  it('shows the active badge on the custom profile when activeCustomProfileId matches', () => {
    useRoutingPreferences.mockReturnValue({
      data: baseData({
        preferredPreset: 'CUSTOM',
        activeCustomProfileId: 11,
        customProfiles: [
          { id: 11, name: 'Work commute', constraints: ['AVOID_STAIRS'], preferredTravelMode: 'WHEELCHAIR' },
        ],
      }),
      isPending: false,
      isError: false,
      refetch,
    })
    render(<RoutingPreferencesSection />)
    const customCard = screen.getByRole('button', { name: /^activate work commute$/i })
    expect(within(customCard).getByText(/^active$/i)).toBeInTheDocument()
    // None of the built-in cards should also be marked active.
    const wheelchairCard = screen.getByRole('button', { name: /^activate wheelchair user$/i })
    expect(within(wheelchairCard).queryByText(/^active$/i)).not.toBeInTheDocument()
  })

  it('disables the create button and explains why when at the cap', () => {
    const profiles = Array.from({ length: 5 }, (_, i) => ({
      id: i + 1, name: `P${i + 1}`, constraints: [], preferredTravelMode: null,
    }))
    useRoutingPreferences.mockReturnValue({
      data: baseData({ customProfiles: profiles }),
      isPending: false,
      isError: false,
      refetch,
    })
    render(<RoutingPreferencesSection />)
    const button = screen.getByRole('button', { name: /create custom preset/i })
    expect(button).toBeDisabled()
    expect(within(button).getByText(/limit reached/i)).toBeInTheDocument()
  })

  it('opens the create modal and submits a new profile', async () => {
    const user = userEvent.setup()
    createMutateAsync.mockResolvedValueOnce({})
    render(<RoutingPreferencesSection />)
    await user.click(screen.getByRole('button', { name: /create custom preset/i }))
    const dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByLabelText(/preset name/i), 'Errand mode')
    await user.click(within(dialog).getByLabelText(/avoid stairs entirely/i))
    await user.click(within(dialog).getByRole('button', { name: /^save$/i }))
    expect(createMutateAsync).toHaveBeenCalledWith({
      name: 'Errand mode',
      constraints: ['AVOID_STAIRS'],
      // Walking is the radio default when creating a fresh preset.
      preferredTravelMode: 'WALKING',
    })
  })

  it('opens the edit modal pre-filled via the details modal and saves changes', async () => {
    const user = userEvent.setup()
    updateMutateAsync.mockResolvedValueOnce({})
    useRoutingPreferences.mockReturnValue({
      data: baseData({
        customProfiles: [
          { id: 11, name: 'Work commute', constraints: ['AVOID_STAIRS'], preferredTravelMode: 'WHEELCHAIR' },
        ],
      }),
      isPending: false,
      isError: false,
      refetch,
    })
    render(<RoutingPreferencesSection />)
    // Open details (info button) → click Edit → editor modal pre-filled.
    await user.click(screen.getByRole('button', { name: /^view work commute details$/i }))
    const detailsDialog = await screen.findByRole('dialog', { name: /work commute details/i })
    await user.click(within(detailsDialog).getByRole('button', { name: /^edit$/i }))
    const editorDialog = await screen.findByRole('dialog', { name: /edit custom routing preset/i })
    expect(within(editorDialog).getByLabelText(/preset name/i)).toHaveValue('Work commute')
    expect(within(editorDialog).getByLabelText(/avoid stairs entirely/i)).toBeChecked()
    await user.click(within(editorDialog).getByLabelText(/prefer routes with ramps/i))
    await user.click(within(editorDialog).getByRole('button', { name: /^save$/i }))
    expect(updateMutateAsync).toHaveBeenCalledWith({
      id: 11,
      body: {
        name: 'Work commute',
        constraints: expect.arrayContaining(['AVOID_STAIRS', 'REQUIRE_RAMPS']),
        preferredTravelMode: 'WHEELCHAIR',
      },
    })
  })

  it('deletes a custom profile from the details modal after inline confirmation', async () => {
    const user = userEvent.setup()
    deleteMutateAsync.mockResolvedValueOnce(null)
    useRoutingPreferences.mockReturnValue({
      data: baseData({
        customProfiles: [
          { id: 11, name: 'Work commute', constraints: [], preferredTravelMode: null },
        ],
      }),
      isPending: false,
      isError: false,
      refetch,
    })
    render(<RoutingPreferencesSection />)
    await user.click(screen.getByRole('button', { name: /^view work commute details$/i }))
    const detailsDialog = await screen.findByRole('dialog', { name: /work commute details/i })
    // First click reveals the inline confirmation step.
    await user.click(within(detailsDialog).getByRole('button', { name: /^delete$/i }))
    expect(deleteMutateAsync).not.toHaveBeenCalled()
    // Second click on "Confirm delete" actually fires the mutation.
    await user.click(within(detailsDialog).getByRole('button', { name: /^confirm delete$/i }))
    expect(deleteMutateAsync).toHaveBeenCalledWith(11)
  })

  it('shows constraint details when info button is clicked on a built-in preset', async () => {
    const user = userEvent.setup()
    render(<RoutingPreferencesSection />)
    await user.click(screen.getByRole('button', { name: /^view wheelchair user details$/i }))
    const dialog = await screen.findByRole('dialog', { name: /wheelchair user details/i })
    // The two constraints in WHEELCHAIR_USER's catalog entry should both be listed.
    expect(within(dialog).getByText(/avoid stairs entirely/i)).toBeInTheDocument()
    expect(within(dialog).getByText(/prefer routes with ramps/i)).toBeInTheDocument()
    // Built-in details modal should not expose Edit/Delete actions.
    expect(within(dialog).queryByRole('button', { name: /^edit$/i })).not.toBeInTheDocument()
    expect(within(dialog).queryByRole('button', { name: /^delete$/i })).not.toBeInTheDocument()
  })

  it('surfaces the API error message in the modal when create fails', async () => {
    const user = userEvent.setup()
    createMutateAsync.mockRejectedValueOnce(
      new Error('Maximum of 5 custom routing profiles reached.')
    )
    render(<RoutingPreferencesSection />)
    await user.click(screen.getByRole('button', { name: /create custom preset/i }))
    const dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByLabelText(/preset name/i), 'Sixth')
    await user.click(within(dialog).getByRole('button', { name: /^save$/i }))
    expect(await within(dialog).findByText(/maximum of 5/i)).toBeInTheDocument()
  })
})
