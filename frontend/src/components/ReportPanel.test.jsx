import { render, screen, waitFor, act, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ReportPanel from './ReportPanel';

describe('ReportPanel', () => {
  let onCloseMock, onVoteChangeMock, onFollowChangeMock, user;

  const report = {
    id: 'r1',
    title: 'Broken Elevator',
    description: 'The elevator is out of service.',
    location: { lat: 41.0683, lng: 29.0505 },
    imageUrl: null,
    userVote: null,
    isFollowed: false,
    comments: [
      { id: 'c1', content: 'Existing comment', author: { id: 'user123', name: 'Tester' } }
    ]
  };

  beforeEach(() => {
    onCloseMock = vi.fn();
    onVoteChangeMock = vi.fn();
    onFollowChangeMock = vi.fn();
    user = userEvent.setup();
  });

  test('renders the report title, description, location and buttons', async () => {
    render(
      <ReportPanel
        report={report}
        onClose={onCloseMock}
        onVoteChange={onVoteChangeMock}
        onFollowChange={onFollowChangeMock}
      />
    );

    expect(screen.getByText(report.title)).toBeInTheDocument();
    expect(screen.getByText(report.description)).toBeInTheDocument();
    expect(screen.getByText(/41\.0683, 29\.0505/)).toBeInTheDocument();
    expect(screen.getByLabelText('Agree')).toBeInTheDocument();
    expect(screen.getByLabelText('Disagree')).toBeInTheDocument();
  });

  test('calls onVoteChange with agree/disagree/null correctly', async () => {
    render(
      <ReportPanel
        report={report}
        onClose={onCloseMock}
        onVoteChange={onVoteChangeMock}
        onFollowChange={onFollowChangeMock}
      />
    );

    const agreeBtn = screen.getByLabelText('Agree');
    const disagreeBtn = screen.getByLabelText('Disagree');

    // Cast agree vote
    await act(async () => user.click(agreeBtn));
    await waitFor(() => expect(onVoteChangeMock).toHaveBeenCalledWith('agree'));

    // Cast disagree vote
    await act(async () => user.click(disagreeBtn));
    await waitFor(() => expect(onVoteChangeMock).toHaveBeenCalledWith('disagree'));

    // Toggle off agree vote
    await act(async () => user.click(agreeBtn));
    await waitFor(() => expect(onVoteChangeMock).toHaveBeenCalledWith(null));
  });

  test('toggles follow state', async () => {
    render(
      <ReportPanel
        report={report}
        onClose={onCloseMock}
        onVoteChange={onVoteChangeMock}
        onFollowChange={onFollowChangeMock}
      />
    );

    const followBtn = screen.getByRole('button', { name: /follow/i });
    await act(async () => user.click(followBtn));

    await waitFor(() => {
      expect(onFollowChangeMock).toHaveBeenCalled();
      expect(followBtn.textContent.toLowerCase()).toMatch(/unfollow/);
    });
  });

  test('can submit a new comment', async () => {
    render(
      <ReportPanel
        report={report}
        onClose={onCloseMock}
        onVoteChange={onVoteChangeMock}
        onFollowChange={onFollowChangeMock}
      />
    );

    const textarea = screen.getByPlaceholderText('Add a comment...');
    const postBtn = screen.getByText(/post/i);

    await user.type(textarea, 'New comment');
    await act(async () => user.click(postBtn));

    await waitFor(() => {
      expect(screen.getByText('New comment')).toBeInTheDocument();
    });
  });

  test('can delete own comment', async () => {
    render(
      <ReportPanel
        report={report}
        onClose={onCloseMock}
        onVoteChange={onVoteChangeMock}
        onFollowChange={onFollowChangeMock}
      />
    );

    const comment = screen.getByText((content, element) =>
      element.tagName.toLowerCase() === 'p' && content.includes('Existing comment')
    );

    const deleteBtn = within(comment.parentElement).getByLabelText('Delete comment');

    await act(async () => user.click(deleteBtn));

    await waitFor(() => {
      expect(comment).not.toBeInTheDocument();
    });
  });
});