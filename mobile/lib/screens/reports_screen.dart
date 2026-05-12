import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
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

// ── Sort options ──────────────────────────────────────────────────────────────

const _kSortOptions = [
  ('NEWEST', 'Newest'),
  ('OLDEST', 'Oldest'),
  ('MOST_AGREED', 'Most agreed'),
  ('MOST_VOTED', 'Most voted'),
  ('DISTANCE', 'Nearest'),
];

String _sortLabel(String id) {
  for (final (sid, label) in _kSortOptions) {
    if (sid == id) return label;
  }
  return id;
}

String _formatDate(DateTime d) {
  const months = [
    'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
    'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec',
  ];
  return '${months[d.month - 1]} ${d.day}, ${d.year}';
}

// ── Advanced filter result ────────────────────────────────────────────────────

class _AdvancedFilterResult {
  final List<ObjectType> objectTypes;
  final List<IssueType> issueTypes;
  final int? authorId;
  final String? authorName;
  final DateTime? publishedAfter;
  final DateTime? publishedBefore;
  final int? minAgrees;
  final int? minDisagrees;

  const _AdvancedFilterResult({
    required this.objectTypes,
    required this.issueTypes,
    this.authorId,
    this.authorName,
    this.publishedAfter,
    this.publishedBefore,
    this.minAgrees,
    this.minDisagrees,
  });

  int get activeCount =>
      (objectTypes.isNotEmpty ? 1 : 0) +
      (issueTypes.isNotEmpty ? 1 : 0) +
      (authorId != null ? 1 : 0) +
      (publishedAfter != null ? 1 : 0) +
      (publishedBefore != null ? 1 : 0) +
      (minAgrees != null ? 1 : 0) +
      (minDisagrees != null ? 1 : 0);
}

// ── Screen ────────────────────────────────────────────────────────────────────

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
  static const double _loadMoreThreshold = 600;

  final ScrollController _scrollController = ScrollController();
  StreamSubscription<SseEvent>? _sseSub;

  // ─── Basic inline filters ───────────────────────────────────────────────────
  ReportType? _typeFilter;
  ReportEnvironment? _envFilter;
  final Set<ReportStatus> _statusFilter = {};
  String _sortOrder = 'NEWEST';

  // ─── Search ─────────────────────────────────────────────────────────────────
  late final TextEditingController _searchCtrl;
  String _searchApplied = '';
  Timer? _searchDebounce;

  // ─── Advanced filters (from bottom sheet) ───────────────────────────────────
  List<ObjectType> _objectTypeFilter = [];
  List<IssueType> _issueTypeFilter = [];
  int? _selectedAuthorId;
  String? _selectedAuthorName;
  DateTime? _publishedAfter;
  DateTime? _publishedBefore;
  int? _minAgrees;
  int? _minDisagrees;

  // ─── Location filter ────────────────────────────────────────────────────────
  LatLng? _searchCenter;
  double _radiusKm = 5.0;
  bool _locating = false;
  bool _locationPanelExpanded = false;

  // ─── Paged state ────────────────────────────────────────────────────────────
  final List<ReportModel> _items = [];
  int _nextPage = 0;
  int? _totalResults;
  bool _isInitialLoading = false;
  bool _isLoadingMore = false;
  bool _reachedEnd = false;
  Object? _error;

  bool _hasNewActivity = false;

  // ─── Computed ───────────────────────────────────────────────────────────────

  bool get _hasActiveFilter =>
      _typeFilter != null ||
      _envFilter != null ||
      _statusFilter.isNotEmpty ||
      _sortOrder != 'NEWEST' ||
      _searchApplied.isNotEmpty ||
      _objectTypeFilter.isNotEmpty ||
      _issueTypeFilter.isNotEmpty ||
      _selectedAuthorId != null ||
      _publishedAfter != null ||
      _publishedBefore != null ||
      _minAgrees != null ||
      _minDisagrees != null;

  int get _advancedFilterCount =>
      (_objectTypeFilter.isNotEmpty ? 1 : 0) +
      (_issueTypeFilter.isNotEmpty ? 1 : 0) +
      (_selectedAuthorId != null ? 1 : 0) +
      (_publishedAfter != null ? 1 : 0) +
      (_publishedBefore != null ? 1 : 0) +
      (_minAgrees != null ? 1 : 0) +
      (_minDisagrees != null ? 1 : 0);

  String? get _feedBlockedReason {
    if (_sortOrder == 'DISTANCE' && _searchCenter == null) {
      return 'Pick a reference point to sort by nearest.';
    }
    return null;
  }

  // ─── Lifecycle ──────────────────────────────────────────────────────────────

  @override
  void initState() {
    super.initState();
    _searchCtrl = TextEditingController();
    _scrollController.addListener(_onScroll);
    WidgetsBinding.instance.addPostFrameCallback((_) => _loadFirstPage());
    _sseSub = context.read<SseService>().events.listen(_onSseEvent);
  }

  @override
  void dispose() {
    _searchCtrl.dispose();
    _searchDebounce?.cancel();
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
      status: _statusFilter.isEmpty ? null : _statusFilter.toList(),
      sort: _sortOrder,
      q: _searchApplied.isEmpty ? null : _searchApplied,
      objectType: _objectTypeFilter.isEmpty ? null : _objectTypeFilter,
      issueType: _issueTypeFilter.isEmpty ? null : _issueTypeFilter,
      authorId: _selectedAuthorId,
      publishedAfter: _publishedAfter?.toUtc().toIso8601String(),
      publishedBefore: _publishedBefore == null
          ? null
          : DateTime(
              _publishedBefore!.year,
              _publishedBefore!.month,
              _publishedBefore!.day,
              23,
              59,
              59,
              999,
            ).toUtc().toIso8601String(),
      minAgrees: _minAgrees,
      minDisagrees: _minDisagrees,
      latitude: _searchCenter?.latitude,
      longitude: _searchCenter?.longitude,
      radiusInKm: _searchCenter == null ? null : _radiusKm,
    );
  }

  Future<void> _loadFirstPage() async {
    if (_feedBlockedReason != null) return;
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
        _totalResults = page.totalElements;
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
    if (_isLoadingMore || _reachedEnd || _feedBlockedReason != null) return;
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
        _error = e;
      });
    }
  }

  Future<void> _refresh() async {
    if (_feedBlockedReason != null) return;
    try {
      final page = await _fetchPage(0);
      if (!mounted) return;
      setState(() {
        _items
          ..clear()
          ..addAll(page.content);
        _nextPage = 1;
        _reachedEnd = page.last;
        _totalResults = page.totalElements;
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

  // ─── Filter handlers ────────────────────────────────────────────────────────

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

  void _toggleStatus(ReportStatus s) {
    setState(() {
      if (_statusFilter.contains(s)) {
        _statusFilter.remove(s);
      } else {
        _statusFilter.add(s);
      }
    });
    _loadFirstPage();
  }

  void _onSearchChanged(String val) {
    _searchDebounce?.cancel();
    _searchDebounce = Timer(const Duration(milliseconds: 350), () {
      if (!mounted) return;
      final trimmed = val.trim();
      if (trimmed == _searchApplied) return;
      setState(() => _searchApplied = trimmed);
      _loadFirstPage();
    });
  }

  Future<void> _openSortSheet() async {
    final chosen = await showModalBottomSheet<String>(
      context: context,
      backgroundColor: AppColors.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (_) => _SortSheet(
        current: _sortOrder,
        hasLocation: _searchCenter != null,
      ),
    );
    if (!mounted || chosen == null || chosen == _sortOrder) return;
    if (chosen == 'DISTANCE' && _searchCenter == null) {
      // Pre-select the sort and open the location panel so the user can set a point.
      setState(() {
        _sortOrder = chosen;
        _locationPanelExpanded = true;
      });
      return; // feed stays blocked until location is set
    }
    setState(() => _sortOrder = chosen);
    _loadFirstPage();
  }

  Future<void> _openAdvancedFilters() async {
    final result = await showModalBottomSheet<_AdvancedFilterResult>(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppColors.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (_) => _AdvancedFiltersSheet(
        api: _api,
        objectTypes: List.from(_objectTypeFilter),
        issueTypes: List.from(_issueTypeFilter),
        authorId: _selectedAuthorId,
        authorName: _selectedAuthorName,
        publishedAfter: _publishedAfter,
        publishedBefore: _publishedBefore,
        minAgrees: _minAgrees,
        minDisagrees: _minDisagrees,
      ),
    );
    if (!mounted || result == null) return;
    setState(() {
      _objectTypeFilter = result.objectTypes;
      _issueTypeFilter = result.issueTypes;
      _selectedAuthorId = result.authorId;
      _selectedAuthorName = result.authorName;
      _publishedAfter = result.publishedAfter;
      _publishedBefore = result.publishedBefore;
      _minAgrees = result.minAgrees;
      _minDisagrees = result.minDisagrees;
    });
    _loadFirstPage();
  }

  void _clearAllFilters() {
    _searchDebounce?.cancel();
    _searchCtrl.clear();
    setState(() {
      _typeFilter = null;
      _envFilter = null;
      _statusFilter.clear();
      _sortOrder = 'NEWEST';
      _searchApplied = '';
      _objectTypeFilter = [];
      _issueTypeFilter = [];
      _selectedAuthorId = null;
      _selectedAuthorName = null;
      _publishedAfter = null;
      _publishedBefore = null;
      _minAgrees = null;
      _minDisagrees = null;
    });
    _loadFirstPage();
  }

  // ─── Location ───────────────────────────────────────────────────────────────

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
    setState(() {
      _searchCenter = null;
      if (_sortOrder == 'DISTANCE') _sortOrder = 'NEWEST';
    });
    _loadFirstPage();
  }

  void _commitRadius(double v) {
    if ((v - _radiusKm).abs() < 0.001) return;
    setState(() => _radiusKm = v);
    if (_searchCenter != null) _loadFirstPage();
  }

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

  // ─── SSE ────────────────────────────────────────────────────────────────────

  void _onSseEvent(SseEvent event) {
    if (!mounted) return;
    switch (event.eventType) {
      case 'REPORT_CREATED':
        if (mounted) setState(() => _hasNewActivity = true);
      case 'REPORT_UPDATED':
        _applyUpdate(event);
      case 'REPORT_DELETED':
        final before = _items.length;
        _items.removeWhere((r) => r.reportId == event.reportId);
        if (_items.length != before && mounted) {
          setState(() {
            if (_totalResults != null) _totalResults = _totalResults! - 1;
          });
        }
      default:
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

  // ─── Build ──────────────────────────────────────────────────────────────────

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
              statusFilter: _statusFilter,
              sortOrder: _sortOrder,
              searchCtrl: _searchCtrl,
              advancedFilterCount: _advancedFilterCount,
              hasActiveFilter: _hasActiveFilter,
              searchCenter: _searchCenter,
              radiusKm: _radiusKm,
              isLocating: _locating,
              expanded: _locationPanelExpanded,
              onToggleExpanded: () =>
                  setState(() => _locationPanelExpanded = !_locationPanelExpanded),
              onTypeChanged: _setTypeFilter,
              onEnvChanged: _setEnvFilter,
              onStatusToggled: _toggleStatus,
              onSearchChanged: _onSearchChanged,
              onOpenSort: _openSortSheet,
              onOpenAdvanced: _openAdvancedFilters,
              onClearAll: _clearAllFilters,
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
    final blocked = _feedBlockedReason;
    if (blocked != null) return _buildBlockedView(blocked);
    if (_isInitialLoading) {
      return Center(child: CircularProgressIndicator(color: AppColors.primary));
    }
    if (_error != null && _items.isEmpty) {
      return _ErrorState(error: _error!, onRetry: _loadFirstPage);
    }
    if (_items.isEmpty) {
      return _EmptyState(
        hasFilters: _hasActiveFilter,
        hasLocation: _searchCenter != null,
        radiusKm: _radiusKm,
      );
    }
    return Column(
      children: [
        if (_totalResults != null) _buildResultCount(_totalResults!),
        Expanded(
          child: RefreshIndicator(
            color: AppColors.primary,
            onRefresh: _refresh,
            child: ListView.separated(
              controller: _scrollController,
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
              itemCount: _items.length + 1,
              separatorBuilder: (context, index) => const SizedBox(height: 12),
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
          ),
        ),
      ],
    );
  }

  Widget _buildResultCount(int count) {
    final label = count == 1 ? '1 result' : '$count results';
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
      child: Align(
        alignment: Alignment.centerLeft,
        child: Text(
          label,
          style: TextStyle(
            fontSize: 13,
            color: AppColors.onSurfaceVariant,
            fontWeight: FontWeight.w500,
          ),
        ),
      ),
    );
  }

  Widget _buildBlockedView(String reason) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 24),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.location_searching, size: 56, color: AppColors.onSurfaceVariant),
          const SizedBox(height: 16),
          Text(
            reason,
            textAlign: TextAlign.center,
            style: TextStyle(
              fontFamily: 'Plus Jakarta Sans',
              fontWeight: FontWeight.w700,
              fontSize: 16,
              color: AppColors.onSurface,
            ),
          ),
          const SizedBox(height: 20),
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              OutlinedButton.icon(
                onPressed: _useMyLocation,
                icon: const Icon(Icons.my_location, size: 16),
                label: const Text('My location'),
                style: OutlinedButton.styleFrom(
                  foregroundColor: AppColors.primary,
                  side: BorderSide(color: AppColors.primary),
                ),
              ),
              const SizedBox(width: 12),
              OutlinedButton.icon(
                onPressed: _pickOnMap,
                icon: const Icon(Icons.map_outlined, size: 16),
                label: const Text('Choose on map'),
                style: OutlinedButton.styleFrom(
                  foregroundColor: AppColors.primary,
                  side: BorderSide(color: AppColors.primary),
                ),
              ),
            ],
          ),
        ],
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
            "You're all caught up.",
            style: TextStyle(
              fontSize: 12,
              color: AppColors.onSurfaceVariant,
            ),
          ),
        ),
      );
    }
    if (_error != null && _items.isNotEmpty) {
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

// ── Header ────────────────────────────────────────────────────────────────────

class _Header extends StatelessWidget {
  final ReportType? typeFilter;
  final ReportEnvironment? envFilter;
  final Set<ReportStatus> statusFilter;
  final String sortOrder;
  final TextEditingController searchCtrl;
  final int advancedFilterCount;
  final bool hasActiveFilter;

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

  final ValueChanged<ReportType?> onTypeChanged;
  final ValueChanged<ReportEnvironment?> onEnvChanged;
  final ValueChanged<ReportStatus> onStatusToggled;
  final ValueChanged<String> onSearchChanged;
  final VoidCallback onOpenSort;
  final VoidCallback onOpenAdvanced;
  final VoidCallback onClearAll;

  const _Header({
    required this.typeFilter,
    required this.envFilter,
    required this.statusFilter,
    required this.sortOrder,
    required this.searchCtrl,
    required this.advancedFilterCount,
    required this.hasActiveFilter,
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
    required this.onTypeChanged,
    required this.onEnvChanged,
    required this.onStatusToggled,
    required this.onSearchChanged,
    required this.onOpenSort,
    required this.onOpenAdvanced,
    required this.onClearAll,
  });

  @override
  Widget build(BuildContext context) {
    final hasLocation = searchCenter != null;
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 12, 20, 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Title row
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

          // Search bar
          TextField(
            controller: searchCtrl,
            onChanged: onSearchChanged,
            textInputAction: TextInputAction.search,
            style: TextStyle(fontSize: 14, color: AppColors.onSurface),
            decoration: InputDecoration(
              hintText: 'Search description…',
              hintStyle: TextStyle(fontSize: 13, color: AppColors.onSurfaceVariant),
              prefixIcon: Icon(Icons.search, size: 18, color: AppColors.onSurfaceVariant),
              contentPadding: const EdgeInsets.symmetric(vertical: 10),
              isDense: true,
              filled: true,
              fillColor: AppColors.surfaceContainer,
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(12),
                borderSide: BorderSide.none,
              ),
              enabledBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(12),
                borderSide: BorderSide.none,
              ),
              focusedBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(12),
                borderSide: BorderSide(color: AppColors.primary, width: 1.5),
              ),
            ),
          ),
          const SizedBox(height: 10),

          // Chip row: type | env | status
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Row(
              children: [
                // Report type
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
                _Divider(),
                // Environment
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
                _Divider(),
                // Status
                ...ReportStatus.values.map((s) => Padding(
                      padding: const EdgeInsets.only(right: 6),
                      child: _FilterChip(
                        label: s.label,
                        selected: statusFilter.contains(s),
                        selectedColor: s.color,
                        onTap: () => onStatusToggled(s),
                      ),
                    )),
              ],
            ),
          ),
          const SizedBox(height: 10),

          // Action row: sort + advanced filters + clear all
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Row(
              children: [
                _ActionPill(
                  icon: Icons.sort,
                  label: _sortLabel(sortOrder),
                  onTap: onOpenSort,
                ),
                const SizedBox(width: 8),
                _ActionPill(
                  icon: Icons.tune,
                  label: advancedFilterCount > 0
                      ? 'Filters ($advancedFilterCount)'
                      : 'Filters',
                  active: advancedFilterCount > 0,
                  onTap: onOpenAdvanced,
                ),
                if (hasActiveFilter) ...[
                  const SizedBox(width: 8),
                  TextButton(
                    onPressed: onClearAll,
                    style: TextButton.styleFrom(
                      foregroundColor: AppColors.error,
                      padding: const EdgeInsets.symmetric(
                          horizontal: 12, vertical: 8),
                      minimumSize: const Size(0, 36),
                      tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                    ),
                    child: const Text(
                      'Clear all',
                      style:
                          TextStyle(fontSize: 12, fontWeight: FontWeight.w700),
                    ),
                  ),
                ],
              ],
            ),
          ),

          // Location panel (collapsible)
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

// ── Sort sheet ────────────────────────────────────────────────────────────────

class _SortSheet extends StatelessWidget {
  final String current;
  final bool hasLocation;

  const _SortSheet({required this.current, required this.hasLocation});

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Center(
            child: Container(
              width: 40,
              height: 4,
              margin: const EdgeInsets.symmetric(vertical: 12),
              decoration: BoxDecoration(
                color: AppColors.outlineVariant,
                borderRadius: BorderRadius.circular(99),
              ),
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 4, 20, 4),
            child: Text(
              'Sort by',
              style: TextStyle(
                fontFamily: 'Plus Jakarta Sans',
                fontWeight: FontWeight.w800,
                fontSize: 18,
                color: AppColors.onSurface,
              ),
            ),
          ),
          for (final (id, label) in _kSortOptions)
            RadioListTile<String>(
              value: id,
              groupValue: current,
              title: Text(
                label,
                style: TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
                  color: (id == 'DISTANCE' && !hasLocation)
                      ? AppColors.onSurfaceVariant
                      : AppColors.onSurface,
                ),
              ),
              subtitle: (id == 'DISTANCE' && !hasLocation)
                  ? Text(
                      'Set a location first',
                      style: TextStyle(
                          fontSize: 11, color: AppColors.onSurfaceVariant),
                    )
                  : null,
              activeColor: AppColors.primary,
              onChanged: (_) => Navigator.pop(context, id),
            ),
          const SizedBox(height: 8),
        ],
      ),
    );
  }
}

// ── Advanced filters sheet ────────────────────────────────────────────────────

class _AdvancedFiltersSheet extends StatefulWidget {
  final ApiService api;
  final List<ObjectType> objectTypes;
  final List<IssueType> issueTypes;
  final int? authorId;
  final String? authorName;
  final DateTime? publishedAfter;
  final DateTime? publishedBefore;
  final int? minAgrees;
  final int? minDisagrees;

  const _AdvancedFiltersSheet({
    required this.api,
    required this.objectTypes,
    required this.issueTypes,
    this.authorId,
    this.authorName,
    this.publishedAfter,
    this.publishedBefore,
    this.minAgrees,
    this.minDisagrees,
  });

  @override
  State<_AdvancedFiltersSheet> createState() => _AdvancedFiltersSheetState();
}

class _AdvancedFiltersSheetState extends State<_AdvancedFiltersSheet> {
  late List<ObjectType> _objectTypes;
  late List<IssueType> _issueTypes;
  int? _authorId;
  String? _authorName;
  late final TextEditingController _authorCtrl;
  List<Map<String, dynamic>> _authorResults = [];
  bool _authorSearching = false;
  Timer? _authorDebounce;
  DateTime? _publishedAfter;
  DateTime? _publishedBefore;
  late final TextEditingController _minAgreesCtrl;
  late final TextEditingController _minDisagreesCtrl;

  @override
  void initState() {
    super.initState();
    _objectTypes = List.from(widget.objectTypes);
    _issueTypes = List.from(widget.issueTypes);
    _authorId = widget.authorId;
    _authorName = widget.authorName;
    _authorCtrl = TextEditingController(text: widget.authorName ?? '');
    _publishedAfter = widget.publishedAfter;
    _publishedBefore = widget.publishedBefore;
    _minAgreesCtrl = TextEditingController(
        text: widget.minAgrees != null ? '${widget.minAgrees}' : '');
    _minDisagreesCtrl = TextEditingController(
        text: widget.minDisagrees != null ? '${widget.minDisagrees}' : '');
  }

  @override
  void dispose() {
    _authorCtrl.dispose();
    _minAgreesCtrl.dispose();
    _minDisagreesCtrl.dispose();
    _authorDebounce?.cancel();
    super.dispose();
  }

  void _toggleObjectType(ObjectType t) {
    setState(() {
      if (_objectTypes.contains(t)) {
        _objectTypes.remove(t);
        _issueTypes.removeWhere(
            (i) => !_objectTypes.any((o) => i.isValidFor(o)));
      } else {
        _objectTypes.add(t);
      }
    });
  }

  void _toggleIssueType(IssueType i) {
    setState(() {
      if (_issueTypes.contains(i)) {
        _issueTypes.remove(i);
      } else {
        _issueTypes.add(i);
      }
    });
  }

  void _onAuthorQueryChanged(String val) {
    _authorDebounce?.cancel();
    if (val.trim().length < 2) {
      if (_authorResults.isNotEmpty) setState(() => _authorResults = []);
      return;
    }
    setState(() => _authorSearching = true);
    _authorDebounce = Timer(const Duration(milliseconds: 350), () async {
      if (!mounted) return;
      try {
        final page = await widget.api.searchUsers(val.trim(), page: 0, size: 8);
        if (!mounted) return;
        setState(() {
          _authorResults = page.content;
          _authorSearching = false;
        });
      } catch (_) {
        if (mounted) setState(() => _authorSearching = false);
      }
    });
  }

  void _selectAuthor(Map<String, dynamic> user) {
    setState(() {
      _authorId = (user['id'] as num?)?.toInt();
      _authorName = user['name'] as String? ?? 'Unknown';
      _authorCtrl.text = _authorName!;
      _authorResults = [];
      _authorSearching = false;
    });
  }

  void _clearAuthor() {
    setState(() {
      _authorId = null;
      _authorName = null;
      _authorCtrl.clear();
      _authorResults = [];
    });
  }

  Future<void> _pickDate(bool isAfter) async {
    final initial = isAfter ? _publishedAfter : _publishedBefore;
    final date = await showDatePicker(
      context: context,
      initialDate: initial ?? DateTime.now(),
      firstDate: DateTime(2020),
      lastDate: DateTime.now().add(const Duration(days: 1)),
      builder: (ctx, child) => Theme(
        data: ThemeData(
          colorScheme: ColorScheme.light(
            primary: AppColors.primary,
            onPrimary: AppColors.onPrimarySolid,
          ),
        ),
        child: child!,
      ),
    );
    if (!mounted || date == null) return;
    setState(() {
      if (isAfter) {
        _publishedAfter = date;
      } else {
        _publishedBefore = date;
      }
    });
  }

  void _clearAll() {
    _authorCtrl.clear();
    _minAgreesCtrl.clear();
    _minDisagreesCtrl.clear();
    setState(() {
      _objectTypes.clear();
      _issueTypes.clear();
      _authorId = null;
      _authorName = null;
      _authorResults = [];
      _publishedAfter = null;
      _publishedBefore = null;
    });
  }

  void _apply() {
    final minAgrees = int.tryParse(_minAgreesCtrl.text.trim());
    final minDisagrees = int.tryParse(_minDisagreesCtrl.text.trim());
    Navigator.pop(
      context,
      _AdvancedFilterResult(
        objectTypes: List.from(_objectTypes),
        issueTypes: List.from(_issueTypes),
        authorId: _authorId,
        authorName: _authorName,
        publishedAfter: _publishedAfter,
        publishedBefore: _publishedBefore,
        minAgrees: minAgrees,
        minDisagrees: minDisagrees,
      ),
    );
  }

  List<(ObjectType, List<IssueType>)> get _issueGroups {
    return _objectTypes
        .map((t) => (t, IssueType.issuesFor(t)))
        .toList();
  }

  @override
  Widget build(BuildContext context) {
    final screenHeight = MediaQuery.of(context).size.height;
    return SizedBox(
      height: screenHeight * 0.88,
      child: Column(
        children: [
          // Drag handle
          Center(
            child: Container(
              width: 40,
              height: 4,
              margin: const EdgeInsets.symmetric(vertical: 12),
              decoration: BoxDecoration(
                color: AppColors.outlineVariant,
                borderRadius: BorderRadius.circular(99),
              ),
            ),
          ),
          // Title + clear all
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 0, 12, 8),
            child: Row(
              children: [
                Text(
                  'Advanced filters',
                  style: TextStyle(
                    fontFamily: 'Plus Jakarta Sans',
                    fontWeight: FontWeight.w800,
                    fontSize: 18,
                    color: AppColors.onSurface,
                  ),
                ),
                const Spacer(),
                TextButton(
                  onPressed: _clearAll,
                  style: TextButton.styleFrom(
                    foregroundColor: AppColors.error,
                    padding: const EdgeInsets.symmetric(horizontal: 8),
                    minimumSize: const Size(0, 32),
                  ),
                  child: const Text('Clear all'),
                ),
              ],
            ),
          ),
          Divider(height: 1, color: AppColors.outlineVariant),
          // Scrollable content
          Expanded(
            child: ListView(
              padding: const EdgeInsets.fromLTRB(20, 16, 20, 20),
              children: [
                _buildSection('Object type', _buildObjectTypeChips()),
                if (_objectTypes.isNotEmpty) ...[
                  const SizedBox(height: 20),
                  _buildSection('Issue type', _buildIssueTypeSection()),
                ],
                const SizedBox(height: 20),
                _buildSection('Author', _buildAuthorSection()),
                const SizedBox(height: 20),
                _buildSection('Published', _buildDateSection()),
                const SizedBox(height: 20),
                _buildSection('Votes', _buildVotesSection()),
                const SizedBox(height: 28),
                FilledButton(
                  onPressed: _apply,
                  style: FilledButton.styleFrom(
                    backgroundColor: AppColors.primary,
                    foregroundColor: AppColors.onPrimarySolid,
                    minimumSize: const Size.fromHeight(48),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(14),
                    ),
                  ),
                  child: const Text(
                    'Apply filters',
                    style: TextStyle(fontWeight: FontWeight.w700),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSection(String title, Widget content) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title.toUpperCase(),
          style: TextStyle(
            fontSize: 11,
            fontWeight: FontWeight.w700,
            letterSpacing: 0.8,
            color: AppColors.onSurfaceVariant,
          ),
        ),
        const SizedBox(height: 8),
        content,
      ],
    );
  }

  Widget _buildObjectTypeChips() {
    return Wrap(
      spacing: 6,
      runSpacing: 6,
      children: ObjectType.values.map((t) {
        final selected = _objectTypes.contains(t);
        return FilterChip(
          label: Text(t.label),
          selected: selected,
          onSelected: (_) => _toggleObjectType(t),
          avatar: Icon(t.icon, size: 14,
              color: selected ? t.color : AppColors.onSurfaceVariant),
          selectedColor: t.color.withValues(alpha: 0.15),
          checkmarkColor: t.color,
          showCheckmark: false,
          side: BorderSide(
            color: selected ? t.color : AppColors.outlineVariant,
            width: selected ? 1.5 : 1,
          ),
          labelStyle: TextStyle(
            fontSize: 12,
            fontWeight: FontWeight.w600,
            color: selected ? t.color : AppColors.onSurface,
          ),
          backgroundColor: AppColors.surfaceContainer,
          padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
        );
      }).toList(),
    );
  }

  Widget _buildIssueTypeSection() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (final (type, issues) in _issueGroups) ...[
          Row(
            children: [
              Container(
                width: 10,
                height: 10,
                margin: const EdgeInsets.only(right: 6),
                decoration: BoxDecoration(
                  color: type.color,
                  shape: BoxShape.circle,
                ),
              ),
              Text(
                type.label,
                style: TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.w700,
                  color: type.color,
                ),
              ),
            ],
          ),
          const SizedBox(height: 6),
          Wrap(
            spacing: 6,
            runSpacing: 6,
            children: issues.map((i) {
              final selected = _issueTypes.contains(i);
              return FilterChip(
                label: Text(i.label),
                selected: selected,
                onSelected: (_) => _toggleIssueType(i),
                selectedColor: type.color.withValues(alpha: 0.15),
                checkmarkColor: type.color,
                side: BorderSide(
                  color: selected ? type.color : AppColors.outlineVariant,
                  width: selected ? 1.5 : 1,
                ),
                labelStyle: TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.w600,
                  color: selected ? type.color : AppColors.onSurface,
                ),
                backgroundColor: AppColors.surfaceContainer,
                padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
              );
            }).toList(),
          ),
          const SizedBox(height: 10),
        ],
      ],
    );
  }

  Widget _buildAuthorSection() {
    if (_authorId != null) {
      return Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        decoration: BoxDecoration(
          color: AppColors.primary.withValues(alpha: 0.08),
          borderRadius: BorderRadius.circular(10),
          border: Border.all(
              color: AppColors.primary.withValues(alpha: 0.3)),
        ),
        child: Row(
          children: [
            Icon(Icons.person, size: 16, color: AppColors.primary),
            const SizedBox(width: 8),
            Expanded(
              child: Text(
                _authorName ?? 'Unknown',
                style: TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
                  color: AppColors.onSurface,
                ),
              ),
            ),
            GestureDetector(
              onTap: _clearAuthor,
              child: Icon(Icons.close, size: 16, color: AppColors.onSurfaceVariant),
            ),
          ],
        ),
      );
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        TextField(
          controller: _authorCtrl,
          onChanged: _onAuthorQueryChanged,
          style: TextStyle(fontSize: 14, color: AppColors.onSurface),
          decoration: InputDecoration(
            hintText: 'Type at least 2 letters…',
            hintStyle: TextStyle(fontSize: 13, color: AppColors.onSurfaceVariant),
            prefixIcon: _authorSearching
                ? Padding(
                    padding: const EdgeInsets.all(12),
                    child: SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(
                          strokeWidth: 2, color: AppColors.primary),
                    ),
                  )
                : Icon(Icons.person_search,
                    size: 18, color: AppColors.onSurfaceVariant),
            isDense: true,
            filled: true,
            fillColor: AppColors.surfaceContainer,
            contentPadding: const EdgeInsets.symmetric(vertical: 10),
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(10),
              borderSide: BorderSide.none,
            ),
            enabledBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(10),
              borderSide: BorderSide.none,
            ),
            focusedBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(10),
              borderSide: BorderSide(color: AppColors.primary, width: 1.5),
            ),
          ),
        ),
        if (_authorResults.isNotEmpty)
          Container(
            margin: const EdgeInsets.only(top: 4),
            decoration: BoxDecoration(
              color: AppColors.surfaceContainerLowest,
              border: Border.all(color: AppColors.outlineVariant),
              borderRadius: BorderRadius.circular(10),
            ),
            child: Column(
              children: _authorResults.map((u) {
                return InkWell(
                  onTap: () => _selectAuthor(u),
                  borderRadius: BorderRadius.circular(10),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 12, vertical: 10),
                    child: Row(
                      children: [
                        Icon(Icons.person_outline,
                            size: 14, color: AppColors.onSurfaceVariant),
                        const SizedBox(width: 8),
                        Text(
                          u['name'] as String? ?? 'Unknown',
                          style: TextStyle(
                              fontSize: 13, color: AppColors.onSurface),
                        ),
                      ],
                    ),
                  ),
                );
              }).toList(),
            ),
          ),
      ],
    );
  }

  Widget _buildDateSection() {
    return Row(
      children: [
        Expanded(
          child: _DatePickerButton(
            label: 'After',
            date: _publishedAfter,
            onTap: () => _pickDate(true),
            onClear: () => setState(() => _publishedAfter = null),
          ),
        ),
        const SizedBox(width: 10),
        Expanded(
          child: _DatePickerButton(
            label: 'Before',
            date: _publishedBefore,
            onTap: () => _pickDate(false),
            onClear: () => setState(() => _publishedBefore = null),
          ),
        ),
      ],
    );
  }

  Widget _buildVotesSection() {
    return Row(
      children: [
        Expanded(
          child: _NumberInput(
            controller: _minAgreesCtrl,
            label: 'Min agrees',
            icon: Icons.thumb_up_outlined,
          ),
        ),
        const SizedBox(width: 10),
        Expanded(
          child: _NumberInput(
            controller: _minDisagreesCtrl,
            label: 'Min disagrees',
            icon: Icons.thumb_down_outlined,
          ),
        ),
      ],
    );
  }
}

// ── Helper widgets for advanced sheet ────────────────────────────────────────

class _DatePickerButton extends StatelessWidget {
  final String label;
  final DateTime? date;
  final VoidCallback onTap;
  final VoidCallback onClear;

  const _DatePickerButton({
    required this.label,
    required this.date,
    required this.onTap,
    required this.onClear,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        decoration: BoxDecoration(
          color: AppColors.surfaceContainer,
          borderRadius: BorderRadius.circular(10),
          border: Border.all(
            color:
                date != null ? AppColors.primary : AppColors.outlineVariant,
          ),
        ),
        child: Row(
          children: [
            Icon(
              Icons.calendar_today,
              size: 14,
              color:
                  date != null ? AppColors.primary : AppColors.onSurfaceVariant,
            ),
            const SizedBox(width: 6),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    label.toUpperCase(),
                    style: TextStyle(
                      fontSize: 9,
                      fontWeight: FontWeight.w700,
                      letterSpacing: 0.5,
                      color: AppColors.onSurfaceVariant,
                    ),
                  ),
                  Text(
                    date != null ? _formatDate(date!) : 'Any date',
                    style: TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                      color: date != null
                          ? AppColors.onSurface
                          : AppColors.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
            ),
            if (date != null)
              GestureDetector(
                onTap: onClear,
                child: Icon(Icons.close,
                    size: 14, color: AppColors.onSurfaceVariant),
              ),
          ],
        ),
      ),
    );
  }
}

class _NumberInput extends StatelessWidget {
  final TextEditingController controller;
  final String label;
  final IconData icon;

  const _NumberInput({
    required this.controller,
    required this.label,
    required this.icon,
  });

  @override
  Widget build(BuildContext context) {
    return TextField(
      controller: controller,
      keyboardType: TextInputType.number,
      inputFormatters: [FilteringTextInputFormatter.digitsOnly],
      style: TextStyle(fontSize: 14, color: AppColors.onSurface),
      decoration: InputDecoration(
        labelText: label,
        labelStyle: TextStyle(fontSize: 12, color: AppColors.onSurfaceVariant),
        prefixIcon: Icon(icon, size: 16, color: AppColors.onSurfaceVariant),
        isDense: true,
        filled: true,
        fillColor: AppColors.surfaceContainer,
        contentPadding: const EdgeInsets.symmetric(vertical: 10),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(10),
          borderSide: BorderSide.none,
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(10),
          borderSide: BorderSide.none,
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(10),
          borderSide: BorderSide(color: AppColors.primary, width: 1.5),
        ),
      ),
    );
  }
}

// ── Location widgets ──────────────────────────────────────────────────────────

String _formatRadius(double km) {
  if (km < 1) return '${(km * 1000).round()} m';
  if (km < 10) return '${km.toStringAsFixed(1)} km';
  return '${km.round()} km';
}

String _formatLatLng(LatLng p) =>
    '${p.latitude.toStringAsFixed(3)}, ${p.longitude.toStringAsFixed(3)}';

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
              color:
                  active ? Colors.transparent : AppColors.outlineVariant,
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
            border: Border.all(
                color: AppColors.primary.withValues(alpha: 0.3)),
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

// ── Shared chip widgets ───────────────────────────────────────────────────────

class _Divider extends StatelessWidget {
  const _Divider();

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 1,
      height: 22,
      margin: const EdgeInsets.symmetric(horizontal: 10),
      color: AppColors.outlineVariant,
    );
  }
}

class _FilterChip extends StatelessWidget {
  final String label;
  final IconData? icon;
  final bool selected;
  final VoidCallback onTap;
  final Color? selectedColor;

  const _FilterChip({
    required this.label,
    this.icon,
    required this.selected,
    required this.onTap,
    this.selectedColor,
  });

  @override
  Widget build(BuildContext context) {
    final bg = selected
        ? (selectedColor ?? AppColors.primary)
        : AppColors.surfaceContainer;
    final fg = selected ? Colors.white : AppColors.onSurface;
    final fgIcon = selected ? Colors.white : AppColors.onSurfaceVariant;
    return Material(
      color: bg,
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
                Icon(icon, size: 14, color: fgIcon),
                const SizedBox(width: 4),
              ],
              Text(
                label,
                style: TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w700,
                  color: fg,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _ActionPill extends StatelessWidget {
  final IconData icon;
  final String label;
  final bool active;
  final VoidCallback onTap;

  const _ActionPill({
    required this.icon,
    required this.label,
    this.active = false,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Material(
      color: active
          ? AppColors.primary.withValues(alpha: 0.1)
          : AppColors.surfaceContainer,
      borderRadius: BorderRadius.circular(999),
      child: InkWell(
        borderRadius: BorderRadius.circular(999),
        onTap: onTap,
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(999),
            border: Border.all(
              color: active ? AppColors.primary : AppColors.outlineVariant,
              width: 1,
            ),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                icon,
                size: 14,
                color: active ? AppColors.primary : AppColors.onSurfaceVariant,
              ),
              const SizedBox(width: 5),
              Text(
                label,
                style: TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w700,
                  color: active ? AppColors.primary : AppColors.onSurface,
                ),
              ),
              const SizedBox(width: 2),
              Icon(
                Icons.expand_more,
                size: 14,
                color: active ? AppColors.primary : AppColors.onSurfaceVariant,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

// ── States ────────────────────────────────────────────────────────────────────

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
                Icon(Icons.arrow_upward, size: 14, color: AppColors.onPrimarySolid),
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
