import 'dart:async';

import 'package:flutter/material.dart';
import 'package:geolocator/geolocator.dart';
import 'package:latlong2/latlong.dart';
import 'package:provider/provider.dart';

import '../models/report_model.dart';
import '../models/sse_event.dart';
import '../services/api_service.dart';
import '../services/auth_service.dart';
import '../services/sse_service.dart';
import '../theme/app_colors.dart';
import '../widgets/report_card.dart';
import 'location_picker_screen.dart';
import 'report_detail_screen.dart';

/// Mobile counterpart to the web `/feed` page. Paginated, filterable
/// scroll of community reports backed by `GET /api/reports/feed`.
class ReportsScreen extends StatefulWidget {
  final void Function(int)? onTabSwitch;

  const ReportsScreen({super.key, this.onTabSwitch});

  @override
  State<ReportsScreen> createState() => _ReportsScreenState();
}

class _ReportsScreenState extends State<ReportsScreen> {
  static const int _pageSize = 20;
  // Trigger the next page when this much extent remains below the viewport.
  static const double _loadMoreThreshold = 600;

  final ScrollController _scrollController = ScrollController();
  StreamSubscription<SseEvent>? _sseSub;

  // Filters
  ReportType? _typeFilter; // null = all
  ReportEnvironment? _envFilter; // null = all

  // Location filter — when [_searchCenter] is non-null, the feed asks the
  // backend for reports within [_radiusKm] of that point and shows distance
  // pills on each card.
  LatLng? _searchCenter;
  double _radiusKm = 5.0;
  bool _locating = false;
  bool _locationPanelExpanded = false;

  // Paged state
  final List<ReportModel> _items = [];
  int _nextPage = 0;
  bool _isInitialLoading = false;
  bool _isLoadingMore = false;
  bool _reachedEnd = false;
  Object? _error;

  // SSE-driven "new activity available" banner.
  bool _hasNewActivity = false;

  @override
  void initState() {
    super.initState();
    _scrollController.addListener(_onScroll);
    WidgetsBinding.instance.addPostFrameCallback((_) => _loadFirstPage());
    _sseSub = context.read<SseService>().events.listen(_onSseEvent);
  }

  @override
  void dispose() {
    _scrollController.removeListener(_onScroll);
    _scrollController.dispose();
    _sseSub?.cancel();
    super.dispose();
  }

  // ─── Data loading ──────────────────────────────────────────────────────────

  ApiService get _api => context.read<AuthService>().api;

  Future<FeedPage<ReportModel>> _fetchPage(int page) {
    return _api.getReportFeed(
      page: page,
      size: _pageSize,
      reportType: _typeFilter,
      environment: _envFilter,
      latitude: _searchCenter?.latitude,
      longitude: _searchCenter?.longitude,
      radiusInKm: _searchCenter == null ? null : _radiusKm,
    );
  }

  Future<void> _loadFirstPage() async {
    if (!mounted) return;
    setState(() {
      _isInitialLoading = _items.isEmpty;
      _error = null;
    });
    try {
      final page = await _fetchPage(0);
      if (!mounted) return;
      setState(() {
        _items
          ..clear()
          ..addAll(page.content);
        _nextPage = 1;
        _reachedEnd = page.last;
        _isInitialLoading = false;
        _hasNewActivity = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _isInitialLoading = false;
        _error = e;
      });
    }
  }

  Future<void> _loadMore() async {
    if (_isLoadingMore || _reachedEnd) return;
    setState(() => _isLoadingMore = true);
    try {
      final page = await _fetchPage(_nextPage);
      if (!mounted) return;
      setState(() {
        _items.addAll(page.content);
        _nextPage += 1;
        _reachedEnd = page.last;
        _isLoadingMore = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _isLoadingMore = false;
        // Surface the error inline at the bottom rather than wiping the list.
        _error = e;
      });
    }
  }

  Future<void> _refresh() async {
    try {
      final page = await _fetchPage(0);
      if (!mounted) return;
      setState(() {
        _items
          ..clear()
          ..addAll(page.content);
        _nextPage = 1;
        _reachedEnd = page.last;
        _error = null;
        _hasNewActivity = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = e);
    }
  }

  void _onScroll() {
    if (_isLoadingMore || _reachedEnd) return;
    if (!_scrollController.hasClients) return;
    final pos = _scrollController.position;
    if (pos.maxScrollExtent - pos.pixels < _loadMoreThreshold) {
      _loadMore();
    }
  }

  void _setTypeFilter(ReportType? next) {
    if (next == _typeFilter) return;
    setState(() => _typeFilter = next);
    _loadFirstPage();
  }

  void _setEnvFilter(ReportEnvironment? next) {
    if (next == _envFilter) return;
    setState(() => _envFilter = next);
    _loadFirstPage();
  }

  /// One-tap "use my location" — reads GPS, sets the search center, refetches.
  /// Permission errors are surfaced as a snackbar rather than failing silently
  /// so the user knows why nothing changed.
  Future<void> _useMyLocation() async {
    if (_locating) return;
    setState(() => _locating = true);
    try {
      LocationPermission permission = await Geolocator.checkPermission();
      if (permission == LocationPermission.denied) {
        permission = await Geolocator.requestPermission();
      }
      if (permission == LocationPermission.denied ||
          permission == LocationPermission.deniedForever) {
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Location permission denied.')),
        );
        return;
      }
      final pos = await Geolocator.getCurrentPosition(
        locationSettings:
            const LocationSettings(accuracy: LocationAccuracy.high),
      ).timeout(const Duration(seconds: 8));
      if (!mounted) return;
      setState(() {
        _searchCenter = LatLng(pos.latitude, pos.longitude);
        _locationPanelExpanded = true;
      });
      _loadFirstPage();
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Could not get current location.')),
      );
    } finally {
      if (mounted) setState(() => _locating = false);
    }
  }

  Future<void> _pickOnMap() async {
    final result = await Navigator.of(context).push<LatLng>(
      MaterialPageRoute(
        builder: (_) => LocationPickerScreen(
          initialLocation: _searchCenter,
          radiusKm: _radiusKm,
        ),
      ),
    );
    if (!mounted || result == null) return;
    setState(() {
      _searchCenter = result;
      _locationPanelExpanded = true;
    });
    _loadFirstPage();
  }

  void _clearLocation() {
    if (_searchCenter == null) return;
    setState(() => _searchCenter = null);
    _loadFirstPage();
  }

  /// Slider drag commit — only refetch when the user lifts their finger.
  /// Live `onChanged` updates the label without firing a network call per pixel.
  void _commitRadius(double v) {
    if ((v - _radiusKm).abs() < 0.001) return;
    setState(() => _radiusKm = v);
    if (_searchCenter != null) _loadFirstPage();
  }

  /// Distance from the active search center to a report, in kilometers.
  /// Returns null when no search center is set, so the card hides the pill.
  double? _distanceTo(ReportModel report) {
    final center = _searchCenter;
    if (center == null) return null;
    final meters = Geolocator.distanceBetween(
      center.latitude,
      center.longitude,
      report.latitude,
      report.longitude,
    );
    return meters / 1000;
  }

  // ─── SSE: react to live report events ──────────────────────────────────────

  void _onSseEvent(SseEvent event) {
    if (!mounted) return;
    switch (event.eventType) {
      case 'REPORT_CREATED':
        // A new report exists somewhere — show the "tap to refresh" banner
        // rather than reordering silently while the user is scrolling.
        if (mounted) setState(() => _hasNewActivity = true);
      case 'REPORT_UPDATED':
        _applyUpdate(event);
      case 'REPORT_DELETED':
        final before = _items.length;
        _items.removeWhere((r) => r.reportId == event.reportId);
        if (_items.length != before && mounted) setState(() {});
      default:
        // MEDIA_ADDED and unknown types: ignore in the feed.
        break;
    }
  }

  void _applyUpdate(SseEvent event) {
    final idx = _items.indexWhere((r) => r.reportId == event.reportId);
    if (idx == -1) return;
    final current = _items[idx];
    final updated = current.copyWith(
      agrees: event.agrees ?? current.agrees,
      disagrees: event.disagrees ?? current.disagrees,
      status: event.status != null
          ? ReportStatus.fromJson(event.status)
          : current.status,
    );
    setState(() => _items[idx] = updated);
  }

  void _openReport(ReportModel report) {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => ReportDetailScreen(report: report),
      ),
    );
  }

  // ─── Build ─────────────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.surface,
      body: SafeArea(
        child: Column(
          children: [
            _Header(
              typeFilter: _typeFilter,
              envFilter: _envFilter,
              onTypeChanged: _setTypeFilter,
              onEnvChanged: _setEnvFilter,
              searchCenter: _searchCenter,
              radiusKm: _radiusKm,
              isLocating: _locating,
              expanded: _locationPanelExpanded,
              onToggleExpanded: () => setState(
                () => _locationPanelExpanded = !_locationPanelExpanded,
              ),
              onUseMyLocation: _useMyLocation,
              onPickOnMap: _pickOnMap,
              onClearLocation: _clearLocation,
              onRadiusPreview: (v) => setState(() => _radiusKm = v),
              onRadiusCommit: _commitRadius,
            ),
            if (_hasNewActivity)
              _NewActivityBanner(onTap: () {
                _scrollController.animateTo(
                  0,
                  duration: const Duration(milliseconds: 320),
                  curve: Curves.easeOut,
                );
                _refresh();
              }),
            Expanded(child: _buildBody()),
          ],
        ),
      ),
    );
  }

  Widget _buildBody() {
    if (_isInitialLoading) {
      return Center(child: CircularProgressIndicator(color: AppColors.primary));
    }
    if (_error != null && _items.isEmpty) {
      return _ErrorState(error: _error!, onRetry: _loadFirstPage);
    }
    if (_items.isEmpty) {
      return _EmptyState(
        hasFilters: _typeFilter != null || _envFilter != null,
        hasLocation: _searchCenter != null,
        radiusKm: _radiusKm,
      );
    }
    return RefreshIndicator(
      color: AppColors.primary,
      onRefresh: _refresh,
      child: ListView.separated(
        controller: _scrollController,
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
        itemCount: _items.length + 1, // +1 for the footer slot
        separatorBuilder: (_, __) => const SizedBox(height: 12),
        itemBuilder: (_, i) {
          if (i == _items.length) return _buildFooter();
          final report = _items[i];
          return ReportCard(
            report: report,
            distanceKm: _distanceTo(report),
            onTap: () => _openReport(report),
          );
        },
      ),
    );
  }

  Widget _buildFooter() {
    if (_isLoadingMore) {
      return Padding(
        padding: const EdgeInsets.symmetric(vertical: 16),
        child: Center(
          child: SizedBox(
            width: 22,
            height: 22,
            child: CircularProgressIndicator(
              strokeWidth: 2,
              color: AppColors.primary,
            ),
          ),
        ),
      );
    }
    if (_reachedEnd && _items.isNotEmpty) {
      return Padding(
        padding: const EdgeInsets.symmetric(vertical: 16),
        child: Center(
          child: Text(
            'You’re all caught up.',
            style: TextStyle(
              fontSize: 12,
              color: AppColors.onSurfaceVariant,
            ),
          ),
        ),
      );
    }
    if (_error != null && _items.isNotEmpty) {
      // Mid-feed error (e.g. failed loadMore). Offer a retry.
      return Padding(
        padding: const EdgeInsets.symmetric(vertical: 16),
        child: Center(
          child: TextButton.icon(
            onPressed: _loadMore,
            icon: const Icon(Icons.refresh),
            label: const Text('Retry'),
            style: TextButton.styleFrom(foregroundColor: AppColors.primary),
          ),
        ),
      );
    }
    return const SizedBox.shrink();
  }
}

// ── Header (title + filter chips) ───────────────────────────────────────────

class _Header extends StatelessWidget {
  final ReportType? typeFilter;
  final ReportEnvironment? envFilter;
  final ValueChanged<ReportType?> onTypeChanged;
  final ValueChanged<ReportEnvironment?> onEnvChanged;

  // Location filter
  final LatLng? searchCenter;
  final double radiusKm;
  final bool isLocating;
  final bool expanded;
  final VoidCallback onToggleExpanded;
  final VoidCallback onUseMyLocation;
  final VoidCallback onPickOnMap;
  final VoidCallback onClearLocation;
  final ValueChanged<double> onRadiusPreview;
  final ValueChanged<double> onRadiusCommit;

  const _Header({
    required this.typeFilter,
    required this.envFilter,
    required this.onTypeChanged,
    required this.onEnvChanged,
    required this.searchCenter,
    required this.radiusKm,
    required this.isLocating,
    required this.expanded,
    required this.onToggleExpanded,
    required this.onUseMyLocation,
    required this.onPickOnMap,
    required this.onClearLocation,
    required this.onRadiusPreview,
    required this.onRadiusCommit,
  });

  @override
  Widget build(BuildContext context) {
    final hasLocation = searchCenter != null;
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 12, 20, 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Feed',
                      style: TextStyle(
                        fontFamily: 'Plus Jakarta Sans',
                        fontWeight: FontWeight.w800,
                        fontSize: 24,
                        color: AppColors.onSurface,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      hasLocation
                          ? 'Within ${_formatRadius(radiusKm)} of '
                              '${_formatLatLng(searchCenter!)}'
                          : 'Latest community reports near and far.',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        fontSize: 12,
                        color: AppColors.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              ),
              _LocationToggleButton(
                active: hasLocation,
                expanded: expanded,
                onTap: onToggleExpanded,
              ),
            ],
          ),
          const SizedBox(height: 12),
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Row(
              children: [
                _FilterChip(
                  label: 'All',
                  selected: typeFilter == null,
                  onTap: () => onTypeChanged(null),
                ),
                const SizedBox(width: 6),
                _FilterChip(
                  label: 'Obstacle',
                  icon: Icons.report_problem_outlined,
                  selected: typeFilter == ReportType.obstacle,
                  onTap: () => onTypeChanged(ReportType.obstacle),
                ),
                const SizedBox(width: 6),
                _FilterChip(
                  label: 'Feature',
                  icon: Icons.accessible_forward,
                  selected: typeFilter == ReportType.feature,
                  onTap: () => onTypeChanged(ReportType.feature),
                ),
                Container(
                  width: 1,
                  height: 22,
                  margin: const EdgeInsets.symmetric(horizontal: 10),
                  color: AppColors.outlineVariant,
                ),
                _FilterChip(
                  label: 'All',
                  selected: envFilter == null,
                  onTap: () => onEnvChanged(null),
                ),
                const SizedBox(width: 6),
                _FilterChip(
                  label: 'Outdoor',
                  icon: Icons.park_outlined,
                  selected: envFilter == ReportEnvironment.outdoor,
                  onTap: () => onEnvChanged(ReportEnvironment.outdoor),
                ),
                const SizedBox(width: 6),
                _FilterChip(
                  label: 'Indoor',
                  icon: Icons.meeting_room_outlined,
                  selected: envFilter == ReportEnvironment.indoor,
                  onTap: () => onEnvChanged(ReportEnvironment.indoor),
                ),
              ],
            ),
          ),
          AnimatedSize(
            duration: const Duration(milliseconds: 200),
            curve: Curves.easeOut,
            alignment: Alignment.topCenter,
            child: expanded
                ? Padding(
                    padding: const EdgeInsets.only(top: 12),
                    child: _LocationPanel(
                      hasLocation: hasLocation,
                      radiusKm: radiusKm,
                      isLocating: isLocating,
                      onUseMyLocation: onUseMyLocation,
                      onPickOnMap: onPickOnMap,
                      onClearLocation: onClearLocation,
                      onRadiusPreview: onRadiusPreview,
                      onRadiusCommit: onRadiusCommit,
                    ),
                  )
                : const SizedBox(width: double.infinity),
          ),
        ],
      ),
    );
  }
}

String _formatRadius(double km) {
  if (km < 1) return '${(km * 1000).round()} m';
  if (km < 10) return '${km.toStringAsFixed(1)} km';
  return '${km.round()} km';
}

String _formatLatLng(LatLng p) {
  return '${p.latitude.toStringAsFixed(3)}, ${p.longitude.toStringAsFixed(3)}';
}

class _LocationToggleButton extends StatelessWidget {
  final bool active;
  final bool expanded;
  final VoidCallback onTap;
  const _LocationToggleButton({
    required this.active,
    required this.expanded,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final fg = active ? AppColors.onPrimarySolid : AppColors.onSurface;
    final bg = active ? AppColors.primary : AppColors.surfaceContainer;
    return Material(
      color: bg,
      borderRadius: BorderRadius.circular(999),
      child: InkWell(
        borderRadius: BorderRadius.circular(999),
        onTap: onTap,
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(999),
            border: Border.all(
              color: active ? Colors.transparent : AppColors.outlineVariant,
              width: 1,
            ),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(active ? Icons.near_me : Icons.near_me_outlined,
                  size: 14, color: fg),
              const SizedBox(width: 4),
              Text(
                'Nearby',
                style: TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 0.4,
                  color: fg,
                ),
              ),
              AnimatedRotation(
                duration: const Duration(milliseconds: 180),
                turns: expanded ? 0.5 : 0,
                child: Icon(Icons.expand_more, size: 16, color: fg),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _LocationPanel extends StatelessWidget {
  // Discrete radius steps (km). Slider drives an index into this list so we
  // get nice round values instead of arbitrary 17.34 km readouts.
  static const List<double> _radiusSteps = [
    0.5, 1, 2, 5, 10, 25, 50, 100, 250, 500,
  ];

  final bool hasLocation;
  final double radiusKm;
  final bool isLocating;
  final VoidCallback onUseMyLocation;
  final VoidCallback onPickOnMap;
  final VoidCallback onClearLocation;
  final ValueChanged<double> onRadiusPreview;
  final ValueChanged<double> onRadiusCommit;

  const _LocationPanel({
    required this.hasLocation,
    required this.radiusKm,
    required this.isLocating,
    required this.onUseMyLocation,
    required this.onPickOnMap,
    required this.onClearLocation,
    required this.onRadiusPreview,
    required this.onRadiusCommit,
  });

  int get _currentIndex {
    final i = _radiusSteps.indexWhere((v) => (v - radiusKm).abs() < 0.001);
    if (i >= 0) return i;
    // Pick the nearest step.
    int best = 0;
    double bestDelta = double.infinity;
    for (var j = 0; j < _radiusSteps.length; j++) {
      final d = (_radiusSteps[j] - radiusKm).abs();
      if (d < bestDelta) {
        bestDelta = d;
        best = j;
      }
    }
    return best;
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(14, 12, 14, 12),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.outlineVariant, width: 1),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: _LocationActionButton(
                  icon: Icons.my_location,
                  label: 'My location',
                  loading: isLocating,
                  onTap: onUseMyLocation,
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: _LocationActionButton(
                  icon: Icons.map_outlined,
                  label: 'Pick on map',
                  onTap: onPickOnMap,
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          Row(
            children: [
              Icon(Icons.radar, size: 14, color: AppColors.onSurfaceVariant),
              const SizedBox(width: 6),
              Text(
                'Radius',
                style: TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w700,
                  color: AppColors.onSurfaceVariant,
                ),
              ),
              const Spacer(),
              Text(
                _formatRadius(radiusKm),
                style: TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w800,
                  color: AppColors.onSurface,
                  fontFeatures: const [FontFeature.tabularFigures()],
                ),
              ),
            ],
          ),
          SliderTheme(
            data: SliderTheme.of(context).copyWith(
              activeTrackColor: AppColors.primary,
              inactiveTrackColor: AppColors.surfaceContainerHigh,
              thumbColor: AppColors.primary,
              overlayColor: AppColors.primary.withValues(alpha: 0.15),
              trackHeight: 3,
            ),
            child: Slider(
              value: _currentIndex.toDouble(),
              min: 0,
              max: (_radiusSteps.length - 1).toDouble(),
              divisions: _radiusSteps.length - 1,
              onChanged: (v) => onRadiusPreview(_radiusSteps[v.round()]),
              onChangeEnd: (v) => onRadiusCommit(_radiusSteps[v.round()]),
            ),
          ),
          if (hasLocation)
            Align(
              alignment: Alignment.centerLeft,
              child: TextButton.icon(
                onPressed: onClearLocation,
                icon: const Icon(Icons.close, size: 16),
                label: const Text('Clear location'),
                style: TextButton.styleFrom(
                  foregroundColor: AppColors.error,
                  padding: const EdgeInsets.symmetric(horizontal: 8),
                  minimumSize: const Size(0, 32),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _LocationActionButton extends StatelessWidget {
  final IconData icon;
  final String label;
  final bool loading;
  final VoidCallback onTap;
  const _LocationActionButton({
    required this.icon,
    required this.label,
    this.loading = false,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Material(
      color: AppColors.primary.withValues(alpha: 0.08),
      borderRadius: BorderRadius.circular(12),
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: loading ? null : onTap,
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: 10),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(12),
            border:
                Border.all(color: AppColors.primary.withValues(alpha: 0.3)),
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              if (loading)
                SizedBox(
                  width: 14,
                  height: 14,
                  child: CircularProgressIndicator(
                    strokeWidth: 2,
                    color: AppColors.primary,
                  ),
                )
              else
                Icon(icon, size: 16, color: AppColors.primary),
              const SizedBox(width: 6),
              Text(
                label,
                style: TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w700,
                  color: AppColors.primary,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _FilterChip extends StatelessWidget {
  final String label;
  final IconData? icon;
  final bool selected;
  final VoidCallback onTap;

  const _FilterChip({
    required this.label,
    this.icon,
    required this.selected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Material(
      color: selected ? AppColors.primary : AppColors.surfaceContainer,
      borderRadius: BorderRadius.circular(999),
      child: InkWell(
        borderRadius: BorderRadius.circular(999),
        onTap: onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 150),
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(999),
            border: Border.all(
              color: selected ? Colors.transparent : AppColors.outlineVariant,
              width: 1,
            ),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (icon != null) ...[
                Icon(
                  icon,
                  size: 14,
                  color: selected
                      ? AppColors.onPrimarySolid
                      : AppColors.onSurfaceVariant,
                ),
                const SizedBox(width: 4),
              ],
              Text(
                label,
                style: TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w700,
                  color: selected
                      ? AppColors.onPrimarySolid
                      : AppColors.onSurface,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

// ── States ──────────────────────────────────────────────────────────────────

class _EmptyState extends StatelessWidget {
  final bool hasFilters;
  final bool hasLocation;
  final double radiusKm;
  const _EmptyState({
    required this.hasFilters,
    required this.hasLocation,
    required this.radiusKm,
  });

  @override
  Widget build(BuildContext context) {
    final title = hasLocation
        ? 'No reports within ${_formatRadius(radiusKm)} of this point.'
        : hasFilters
            ? 'No reports match your filters.'
            : 'No reports yet.';
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 32),
      child: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              hasLocation ? Icons.location_off : Icons.assignment_outlined,
              size: 56,
              color: AppColors.onSurfaceVariant,
            ),
            const SizedBox(height: 12),
            Text(
              title,
              textAlign: TextAlign.center,
              style: TextStyle(
                fontFamily: 'Plus Jakarta Sans',
                fontWeight: FontWeight.w700,
                fontSize: 16,
                color: AppColors.onSurface,
              ),
            ),
            const SizedBox(height: 6),
            Text(
              hasLocation
                  ? 'Try widening the radius or moving the search center.'
                  : hasFilters
                      ? 'Try clearing a filter to widen the search.'
                      : 'Be the first to submit one from the map tab.',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 12,
                color: AppColors.onSurfaceVariant,
                height: 1.4,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _ErrorState extends StatelessWidget {
  final Object error;
  final VoidCallback onRetry;
  const _ErrorState({required this.error, required this.onRetry});

  @override
  Widget build(BuildContext context) {
    final message = error is ApiException
        ? (error as ApiException).userMessage
        : 'Could not load the feed.';
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 32),
      child: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.wifi_off, size: 56, color: AppColors.error),
            const SizedBox(height: 12),
            Text(
              'Could not load the feed',
              style: TextStyle(
                fontFamily: 'Plus Jakarta Sans',
                fontWeight: FontWeight.w700,
                fontSize: 16,
                color: AppColors.onSurface,
              ),
            ),
            const SizedBox(height: 6),
            Text(
              message,
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 12,
                color: AppColors.onSurfaceVariant,
                height: 1.4,
              ),
            ),
            const SizedBox(height: 16),
            FilledButton.icon(
              onPressed: onRetry,
              icon: const Icon(Icons.refresh),
              label: const Text('Try Again'),
              style: FilledButton.styleFrom(
                backgroundColor: AppColors.primary,
                foregroundColor: AppColors.onPrimarySolid,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _NewActivityBanner extends StatelessWidget {
  final VoidCallback onTap;
  const _NewActivityBanner({required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
      child: Material(
        color: AppColors.primary,
        borderRadius: BorderRadius.circular(999),
        child: InkWell(
          borderRadius: BorderRadius.circular(999),
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(
                  Icons.arrow_upward,
                  size: 14,
                  color: AppColors.onPrimarySolid,
                ),
                const SizedBox(width: 6),
                Text(
                  'New reports — tap to refresh',
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                    color: AppColors.onPrimarySolid,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
