import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../services/api_service.dart';
import '../services/auth_service.dart';
import '../services/theme_service.dart';
import '../theme/app_colors.dart';
import 'user_profile_screen.dart';

/// Mirrors the web `UserSearchPage` — paginated, debounced search by name or
/// email against `GET /api/users/search`. Requires authentication; the bell
/// affordance that opens this screen is itself gated on auth.
class UserSearchScreen extends StatefulWidget {
  const UserSearchScreen({super.key});

  @override
  State<UserSearchScreen> createState() => _UserSearchScreenState();
}

class _UserSearchScreenState extends State<UserSearchScreen> {
  // Matches frontend's MIN_QUERY_LENGTH/DEBOUNCE_MS — keeps the two surfaces
  // hitting the backend with the same UX shape.
  static const int _minLength = 2;
  static const Duration _debounce = Duration(milliseconds: 300);

  final TextEditingController _controller = TextEditingController();
  Timer? _debounceTimer;

  String _query = '';
  int _page = 0;
  bool _loading = false;
  String? _error;
  FeedPage<Map<String, dynamic>>? _result;

  @override
  void dispose() {
    _debounceTimer?.cancel();
    _controller.dispose();
    super.dispose();
  }

  void _onQueryChanged(String value) {
    _debounceTimer?.cancel();
    _debounceTimer = Timer(_debounce, () {
      final trimmed = value.trim();
      if (trimmed == _query) return;
      setState(() {
        _query = trimmed;
        _page = 0;
        _result = null;
        _error = null;
      });
      if (trimmed.length >= _minLength) _runSearch();
    });
  }

  Future<void> _runSearch() async {
    final api = context.read<AuthService>().api;
    final pageToFetch = _page;
    final queryToFetch = _query;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final result = await api.searchUsers(queryToFetch, page: pageToFetch);
      if (!mounted) return;
      // Drop the response if the user has typed something newer since.
      if (queryToFetch != _query || pageToFetch != _page) return;
      setState(() {
        _result = result;
        _loading = false;
      });
    } on ApiException catch (e) {
      if (!mounted || queryToFetch != _query) return;
      setState(() {
        _error = e.userMessage;
        _loading = false;
      });
    } catch (_) {
      if (!mounted || queryToFetch != _query) return;
      setState(() {
        _error = 'Search failed.';
        _loading = false;
      });
    }
  }

  void _goToPage(int newPage) {
    setState(() {
      _page = newPage;
      _result = null;
    });
    _runSearch();
  }

  @override
  Widget build(BuildContext context) {
    // Pull theme so AppColors getters resolve against the live brightness.
    context.watch<ThemeService>();
    final trimmed = _query;
    final tooShort = trimmed.isNotEmpty && trimmed.length < _minLength;

    return Scaffold(
      backgroundColor: AppColors.surface,
      appBar: AppBar(
        backgroundColor: AppColors.surfaceTint,
        foregroundColor: AppColors.onSurface,
        elevation: 0,
        title: Text(
          'Find people',
          style: TextStyle(
            fontFamily: 'Plus Jakarta Sans',
            fontWeight: FontWeight.w800,
            color: AppColors.onSurface,
          ),
        ),
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              _buildSearchField(),
              const SizedBox(height: 12),
              if (tooShort)
                Padding(
                  padding: const EdgeInsets.only(top: 4, bottom: 4),
                  child: Text(
                    'Type at least $_minLength characters to search.',
                    style: TextStyle(
                      fontSize: 13,
                      color: AppColors.onSurfaceVariant,
                    ),
                  ),
                ),
              Expanded(child: _buildBody()),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildSearchField() {
    return TextField(
      controller: _controller,
      onChanged: _onQueryChanged,
      autofocus: true,
      textInputAction: TextInputAction.search,
      style: TextStyle(color: AppColors.onSurface),
      decoration: InputDecoration(
        hintText: 'Search by name or email',
        hintStyle: TextStyle(color: AppColors.onSurfaceVariant),
        prefixIcon: Icon(Icons.search, color: AppColors.onSurfaceVariant),
        suffixIcon: _controller.text.isEmpty
            ? null
            : IconButton(
                icon: Icon(Icons.clear, color: AppColors.onSurfaceVariant),
                onPressed: () {
                  _controller.clear();
                  _onQueryChanged('');
                },
              ),
        filled: true,
        fillColor: AppColors.surfaceContainerLowest,
        contentPadding:
            const EdgeInsets.symmetric(horizontal: 12, vertical: 14),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: BorderSide.none,
        ),
      ),
    );
  }

  Widget _buildBody() {
    if (_query.length < _minLength) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 24),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(Icons.person_search,
                  size: 48, color: AppColors.onSurfaceVariant),
              const SizedBox(height: 12),
              Text(
                'Search for other Mapcess users',
                style: TextStyle(
                  fontFamily: 'Plus Jakarta Sans',
                  fontWeight: FontWeight.w700,
                  fontSize: 16,
                  color: AppColors.onSurface,
                ),
              ),
            ],
          ),
        ),
      );
    }

    if (_loading && _result == null) {
      return Center(child: CircularProgressIndicator(color: AppColors.primary));
    }

    if (_error != null) {
      return Center(
        child: Text(
          _error!,
          style: TextStyle(color: AppColors.error, fontSize: 14),
          textAlign: TextAlign.center,
        ),
      );
    }

    final result = _result;
    if (result == null) {
      return const SizedBox.shrink();
    }
    if (result.content.isEmpty) {
      return Center(
        child: Text(
          'No users found.',
          style: TextStyle(color: AppColors.onSurfaceVariant),
        ),
      );
    }

    return Column(
      children: [
        Align(
          alignment: Alignment.centerLeft,
          child: Padding(
            padding: const EdgeInsets.only(left: 4, bottom: 8),
            child: Text(
              _matchCountLabel(result),
              style: TextStyle(
                fontSize: 12,
                color: AppColors.onSurfaceVariant,
              ),
            ),
          ),
        ),
        Expanded(
          child: ListView.separated(
            itemCount: result.content.length,
            separatorBuilder: (_, __) => const SizedBox(height: 8),
            itemBuilder: (_, i) => _UserResultTile(user: result.content[i]),
          ),
        ),
        if (result.totalPages > 1) _buildPager(result),
      ],
    );
  }

  String _matchCountLabel(FeedPage<Map<String, dynamic>> result) {
    // Backend's Page<T> doesn't surface totalElements through FeedPage today;
    // fall back to a per-page summary so the count stays honest.
    final lastPage = result.last;
    final count = result.content.length;
    if (lastPage && _page == 0) {
      return '$count match${count == 1 ? '' : 'es'}.';
    }
    return 'Page ${_page + 1} of ${result.totalPages}.';
  }

  Widget _buildPager(FeedPage<Map<String, dynamic>> result) {
    final isFirst = _page == 0;
    final isLast = result.last;
    return Padding(
      padding: const EdgeInsets.only(top: 12),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          TextButton(
            onPressed: isFirst || _loading
                ? null
                : () => _goToPage(_page - 1),
            child: const Text('Previous'),
          ),
          const SizedBox(width: 8),
          Text(
            'Page ${_page + 1} of ${result.totalPages}',
            style: TextStyle(
              fontSize: 12,
              color: AppColors.onSurfaceVariant,
            ),
          ),
          const SizedBox(width: 8),
          FilledButton(
            onPressed: isLast || _loading ? null : () => _goToPage(_page + 1),
            child: const Text('Next'),
          ),
        ],
      ),
    );
  }
}

class _UserResultTile extends StatelessWidget {
  final Map<String, dynamic> user;
  const _UserResultTile({required this.user});

  @override
  Widget build(BuildContext context) {
    final id = (user['id'] as num?)?.toInt();
    final name = user['name'] as String? ?? 'Unknown';
    final avatarUrl = user['avatarUrl'] as String?;
    final role = user['role'] as String?;
    final stats = user['contributionStats'] as Map<String, dynamic>?;
    final reports = (stats?['reportsSubmitted'] as num?)?.toInt() ?? 0;
    final routes = (stats?['routesPlanned'] as num?)?.toInt() ?? 0;

    return Material(
      color: AppColors.surfaceContainerLowest,
      borderRadius: BorderRadius.circular(16),
      child: InkWell(
        borderRadius: BorderRadius.circular(16),
        onTap: id == null
            ? null
            : () => Navigator.of(context).push(
                  MaterialPageRoute(
                    builder: (_) => UserProfileScreen(
                      userId: id,
                      initialName: name,
                      initialAvatarUrl: avatarUrl,
                    ),
                  ),
                ),
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Row(
            children: [
              _avatar(avatarUrl),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      name,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        fontWeight: FontWeight.w700,
                        color: AppColors.onSurface,
                        fontSize: 14,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      '$reports report${reports == 1 ? '' : 's'} '
                      '· $routes route${routes == 1 ? '' : 's'}',
                      style: TextStyle(
                        fontSize: 12,
                        color: AppColors.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              ),
              if (role != null)
                Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: AppColors.primary.withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(999),
                  ),
                  child: Text(
                    role,
                    style: TextStyle(
                      fontSize: 10,
                      fontWeight: FontWeight.w700,
                      letterSpacing: 0.4,
                      color: AppColors.primary,
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _avatar(String? avatarUrl) {
    return Container(
      width: 44,
      height: 44,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: AppColors.surfaceContainerHigh,
      ),
      child: ClipOval(
        child: (avatarUrl != null && avatarUrl.isNotEmpty)
            ? Image.network(
                avatarUrl,
                fit: BoxFit.cover,
                errorBuilder: (_, __, ___) => _fallback(),
              )
            : _fallback(),
      ),
    );
  }

  Widget _fallback() => Center(
        child: Icon(Icons.person, size: 22, color: AppColors.secondary),
      );
}
