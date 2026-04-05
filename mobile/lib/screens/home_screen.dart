import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:geolocator/geolocator.dart';
import 'package:http/http.dart' as http;
import 'package:latlong2/latlong.dart';
import 'package:provider/provider.dart';
import '../theme/app_colors.dart';
import '../models/report_model.dart';
import '../services/auth_service.dart';
import 'report_detail_screen.dart';
import 'make_report_screen.dart';
import 'login_screen.dart';

class HomeScreen extends StatefulWidget {
  final void Function(int)? onTabSwitch;

  const HomeScreen({super.key, this.onTabSwitch});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final MapController _mapController = MapController();
  List<Marker> _markers = [];
  bool _loading = true;
  String? _errorMessage;

  // ── Search state ──────────────────────────────────────────────────────────
  bool _searchActive = false;
  final TextEditingController _searchController = TextEditingController();
  final FocusNode _searchFocus = FocusNode();
  List<_Place> _searchResults = [];
  bool _searchLoading = false;
  Timer? _debounce;

  // ── Searched place pin ────────────────────────────────────────────────────
  LatLng? _searchedPin;
  String _searchedPinLabel = '';

  // ── User location ─────────────────────────────────────────────────────────
  LatLng? _userLocation;
  StreamSubscription<Position>? _locationStream;

  // ── Route state ───────────────────────────────────────────────────────────
  bool _routeMode = false;
  bool _routeLoading = false;
  List<_RouteData> _routes = [];
  int _selectedRouteIdx = 0;
  String? _routeError;
  LatLng? _routeStart;          // null = use current location
  String _routeStartLabel = 'Current Location';
  LatLng? _routeEnd;
  String _routeEndLabel = '';
  bool _editingStart = false;   // which field is open for search
  bool _routePanelExpanded = true; // collapsed when routes are loaded

  static const LatLng _defaultCenter = LatLng(37.7599, -122.4148);

  @override
  void initState() {
    super.initState();
    _initLocation();
    _loadReports();
  }

  Future<void> _initLocation() async {
    try {
      LocationPermission permission = await Geolocator.checkPermission();
      if (permission == LocationPermission.denied) {
        permission = await Geolocator.requestPermission();
      }
      if (permission == LocationPermission.denied ||
          permission == LocationPermission.deniedForever) return;

      // Try high accuracy first; fall back to low if it times out
      Position? pos;
      try {
        pos = await Geolocator.getCurrentPosition(
          locationSettings: const LocationSettings(accuracy: LocationAccuracy.high),
        ).timeout(const Duration(seconds: 8));
      } catch (_) {
        pos = await Geolocator.getCurrentPosition(
          locationSettings: const LocationSettings(accuracy: LocationAccuracy.low),
        ).timeout(const Duration(seconds: 6));
      }

      if (!mounted) return;
      final loc = LatLng(pos.latitude, pos.longitude);
      setState(() => _userLocation = loc);
      _mapController.move(loc, 15);

      // Keep location dot updated
      _locationStream = Geolocator.getPositionStream(
        locationSettings: const LocationSettings(
          accuracy: LocationAccuracy.medium,
          distanceFilter: 10,
        ),
      ).listen((p) {
        if (mounted) setState(() => _userLocation = LatLng(p.latitude, p.longitude));
      });
    } catch (_) {}
  }

  @override
  void dispose() {
    _locationStream?.cancel();
    _mapController.dispose();
    _searchController.dispose();
    _searchFocus.dispose();
    _debounce?.cancel();
    super.dispose();
  }

  // ── Nominatim geocoding ───────────────────────────────────────────────────

  void _onSearchChanged(String query) {
    _debounce?.cancel();
    if (query.trim().isEmpty) {
      setState(() => _searchResults = []);
      return;
    }
    _debounce = Timer(const Duration(milliseconds: 500), () => _search(query));
  }

  Future<void> _search(String query) async {
    setState(() => _searchLoading = true);
    try {
      final uri = Uri.https('nominatim.openstreetmap.org', '/search', {
        'q': query,
        'format': 'json',
        'limit': '5',
      });
      final response = await http.get(uri, headers: {
        'User-Agent': 'Mapcess/1.0 (bounswe2026group1)',
        'Accept-Language': 'en',
      });
      if (!mounted) return;
      if (response.statusCode == 200) {
        final list = jsonDecode(response.body) as List<dynamic>;
        setState(() {
          _searchResults = list
              .map((e) => _Place.fromJson(e as Map<String, dynamic>))
              .toList();
        });
      }
    } catch (_) {
    } finally {
      if (mounted) setState(() => _searchLoading = false);
    }
  }

  void _selectPlace(_Place place) {
    final loc = LatLng(place.lat, place.lon);
    final shortName = place.displayName.split(',').first;
    setState(() {
      _searchActive = false;
      _searchResults = [];
      _searchController.clear();
    });
    _searchFocus.unfocus();

    if (_routeMode) {
      if (_editingStart) {
        setState(() {
          _routeStart = loc;
          _routeStartLabel = shortName;
          _editingStart = false;
        });
        if (_routeEnd != null) _fetchRoute(_routeEnd!);
      } else {
        setState(() {
          _routeEnd = loc;
          _routeEndLabel = shortName;
          _editingStart = false;
        });
        _fetchRoute(loc);
      }
    } else {
      setState(() {
        _searchedPin = loc;
        _searchedPinLabel = shortName;
      });
      _mapController.move(loc, 15);
    }
  }

  void _openSearch() {
    setState(() {
      _searchActive = true;
      _searchResults = [];
    });
    Future.delayed(const Duration(milliseconds: 50), _searchFocus.requestFocus);
  }

  void _closeSearch() {
    setState(() {
      _searchActive = false;
      _searchResults = [];
      _searchController.clear();
      if (!_routeMode) { _searchedPin = null; _searchedPinLabel = ''; }
    });
    _searchFocus.unfocus();
  }

  // ── Route logic ───────────────────────────────────────────────────────────

  void _enterRouteMode() {
    final prefillEnd = _searchedPin;
    final prefillEndLabel = _searchedPinLabel;
    setState(() {
      _routeMode = true;
      _routes = [];
      _routeError = null;
      _routeStart = null;
      _routeStartLabel =
          _userLocation != null ? 'Current Location' : 'Set starting point';
      _routeEnd = prefillEnd;
      _routeEndLabel = prefillEndLabel;
      _searchedPin = null;
      _searchedPinLabel = '';
      _editingStart = false;
      _routePanelExpanded = true;
    });
    // Auto-fetch if we have a destination ready
    if (prefillEnd != null) _fetchRoute(prefillEnd);
  }

  void _cancelRoute() {
    setState(() {
      _routeMode = false;
      _routes = [];
      _selectedRouteIdx = 0;
      _routeError = null;
      _routeStart = null;
      _routeStartLabel = 'Current Location';
      _routeEnd = null;
      _routeEndLabel = '';
      _editingStart = false;
      _routePanelExpanded = true;
    });
    _closeSearch();
  }

  void _swapRoutePoints() {
    final swappedStart = _routeEnd;
    final swappedStartLabel = _routeEndLabel.isNotEmpty ? _routeEndLabel : 'Current Location';
    final swappedEnd = _routeStart ?? _userLocation;
    final swappedEndLabel = _routeStart != null ? _routeStartLabel : 'Current Location';
    setState(() {
      _routeStart = swappedStart;
      _routeStartLabel = swappedStartLabel;
      _routeEnd = swappedEnd;
      _routeEndLabel = swappedEndLabel;
    });
    if (_routeEnd != null) _fetchRoute(_routeEnd!);
  }

  /// Decodes a Google-encoded polyline string into a list of LatLng points.
  List<LatLng> _decodePolyline(String encoded) {
    final points = <LatLng>[];
    int index = 0;
    int lat = 0;
    int lng = 0;
    while (index < encoded.length) {
      int shift = 0, result = 0, b;
      do {
        b = encoded.codeUnitAt(index++) - 63;
        result |= (b & 0x1f) << shift;
        shift += 5;
      } while (b >= 0x20);
      lat += (result & 1) != 0 ? ~(result >> 1) : result >> 1;

      shift = 0;
      result = 0;
      do {
        b = encoded.codeUnitAt(index++) - 63;
        result |= (b & 0x1f) << shift;
        shift += 5;
      } while (b >= 0x20);
      lng += (result & 1) != 0 ? ~(result >> 1) : result >> 1;

      points.add(LatLng(lat / 1e5, lng / 1e5));
    }
    return points;
  }

  Future<void> _fetchRoute(LatLng destination) async {
    final start = _routeStart ?? _userLocation;
    if (start == null) {
      setState(() {
        _routeLoading = false;
        _routeError = 'No starting point set. Tap "From" to choose one.';
      });
      return;
    }
    setState(() {
      _routeLoading = true;
      _routeError = null;
      _routes = [];
    });
    try {
      final rawRoutes = await context.read<AuthService>().api.getRoutes(
            startLat: start.latitude,
            startLon: start.longitude,
            endLat: destination.latitude,
            endLon: destination.longitude,
          );
      if (!mounted) return;
      if (rawRoutes.isEmpty) {
        setState(() {
          _routeLoading = false;
          _routeError = 'No route found.';
        });
        return;
      }

      final decoded = rawRoutes.map((r) {
        final geometry = r['geometry'] as String? ?? '';
        final points = geometry.isNotEmpty ? _decodePolyline(geometry) : <LatLng>[];
        return _RouteData.fromJson(r, points);
      }).where((r) => r.points.isNotEmpty).toList();

      if (decoded.isEmpty) {
        setState(() {
          _routeLoading = false;
          _routeError = 'No route found.';
        });
        return;
      }

      // Fit map to show the first route
      final allPoints = decoded.first.points;
      if (allPoints.isNotEmpty) {
        final lats = allPoints.map((p) => p.latitude);
        final lons = allPoints.map((p) => p.longitude);
        final centerLat = lats.reduce((a, b) => a + b) / allPoints.length;
        final centerLon = lons.reduce((a, b) => a + b) / allPoints.length;
        _mapController.move(LatLng(centerLat, centerLon), 13);
      }

      setState(() {
        _routes = decoded;
        _selectedRouteIdx = 0;
        _routeLoading = false;
        _routePanelExpanded = false;
      });
    } catch (_) {
      if (mounted) {
        setState(() {
          _routeLoading = false;
          _routeError = 'Could not find a route. Try again.';
        });
      }
    }
  }

  // ── Reports ───────────────────────────────────────────────────────────────

  Future<void> _loadReports() async {
    setState(() {
      _loading = true;
      _errorMessage = null;
    });
    try {
      final api = context.read<AuthService>().api;
      final reports = await api.getReports();
      if (!mounted) return;
      setState(() {
        _markers = _buildMarkers(reports);
        _loading = false;
      });
      if (reports.isNotEmpty && _userLocation == null) {
        _mapController.move(_centerOf(reports), 14);
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _loading = false;
          _errorMessage = e.toString();
        });
      }
    }
  }

  LatLng _centerOf(List<ReportModel> reports) {
    final lat = reports.map((r) => r.latitude).reduce((a, b) => a + b) /
        reports.length;
    final lon = reports.map((r) => r.longitude).reduce((a, b) => a + b) /
        reports.length;
    return LatLng(lat, lon);
  }

  List<Marker> _buildMarkers(List<ReportModel> reports) {
    return reports.map((report) {
      return Marker(
        point: LatLng(report.latitude, report.longitude),
        width: 88,
        height: 72,
        child: GestureDetector(
          onTap: () => Navigator.push(
            context,
            MaterialPageRoute(
              builder: (_) => ReportDetailScreen(report: report),
            ),
          ),
          child: Column(
            children: [
              Container(
                padding: const EdgeInsets.all(4),
                decoration: BoxDecoration(
                  color: Colors.white,
                  shape: BoxShape.circle,
                  border: Border.all(
                    color: AppColors.primary.withOpacity(0.12),
                  ),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withOpacity(0.14),
                      blurRadius: 10,
                      offset: const Offset(0, 3),
                    ),
                  ],
                ),
                child: Container(
                  width: 34,
                  height: 34,
                  decoration: BoxDecoration(
                    color: report.tag.color,
                    shape: BoxShape.circle,
                  ),
                  child: Icon(report.tag.icon, color: Colors.white, size: 17),
                ),
              ),
              const SizedBox(height: 4),
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: Colors.white.withOpacity(0.93),
                  borderRadius: BorderRadius.circular(8),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withOpacity(0.1),
                      blurRadius: 6,
                      offset: const Offset(0, 2),
                    ),
                  ],
                ),
                child: Text(
                  report.tag.label,
                  style: const TextStyle(
                    fontSize: 8,
                    fontWeight: FontWeight.w700,
                    letterSpacing: 0.8,
                    color: AppColors.onSurfaceVariant,
                  ),
                ),
              ),
            ],
          ),
        ),
      );
    }).toList();
  }

  // ── Build ─────────────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Stack(
        children: [
          // ── Full-screen map ─────────────────────────────────────────────
          FlutterMap(
            mapController: _mapController,
            options: const MapOptions(
              initialCenter: _defaultCenter,
              initialZoom: 14,
            ),
            children: [
              TileLayer(
                urlTemplate:
                    'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
                userAgentPackageName: 'com.bounswe2026group1.mapcess',
              ),
              // Route polylines (one per route option)
              if (_routes.isNotEmpty)
                PolylineLayer(
                  polylines: [
                    // Unselected routes first (drawn underneath)
                    for (int i = 0; i < _routes.length; i++)
                      if (i != _selectedRouteIdx)
                        Polyline(
                          points: _routes[i].points,
                          color: _routes[i].color.withOpacity(0.25),
                          strokeWidth: 3.5,
                        ),
                    // Selected route on top
                    if (_routes.isNotEmpty)
                      Polyline(
                        points: _routes[_selectedRouteIdx].points,
                        color: _routes[_selectedRouteIdx].color,
                        strokeWidth: 6.0,
                      ),
                  ],
                ),
              MarkerLayer(markers: [
                ..._markers,
                // User location dot
                if (_userLocation != null)
                  Marker(
                    point: _userLocation!,
                    width: 24,
                    height: 24,
                    child: Container(
                      decoration: BoxDecoration(
                        color: const Color(0xFF1A73E8),
                        shape: BoxShape.circle,
                        border: Border.all(color: Colors.white, width: 3),
                        boxShadow: [
                          BoxShadow(
                            color: const Color(0xFF1A73E8).withOpacity(0.4),
                            blurRadius: 8,
                            spreadRadius: 2,
                          ),
                        ],
                      ),
                    ),
                  ),
                // Normal search pin
                if (!_routeMode && _searchedPin != null)
                  Marker(
                    point: _searchedPin!,
                    width: 36,
                    height: 44,
                    alignment: Alignment.bottomCenter,
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Container(
                          width: 30,
                          height: 30,
                          decoration: BoxDecoration(
                            color: AppColors.primary,
                            shape: BoxShape.circle,
                            border: Border.all(color: Colors.white, width: 2.5),
                            boxShadow: [
                              BoxShadow(
                                color: Colors.black.withOpacity(0.25),
                                blurRadius: 6,
                                offset: const Offset(0, 3),
                              ),
                            ],
                          ),
                          child: const Icon(
                            Icons.place,
                            color: Colors.white,
                            size: 16,
                          ),
                        ),
                        Container(
                          width: 2.5,
                          height: 10,
                          color: AppColors.primary,
                        ),
                      ],
                    ),
                  ),
                // Route start pin
                if (_routeMode && _routes.isNotEmpty)
                  Marker(
                    point: _routeStart ?? _userLocation!,
                    width: 36,
                    height: 44,
                    alignment: Alignment.bottomCenter,
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Container(
                          width: 30,
                          height: 30,
                          decoration: BoxDecoration(
                            color: const Color(0xFF2E7D32),
                            shape: BoxShape.circle,
                            border: Border.all(color: Colors.white, width: 2.5),
                            boxShadow: [
                              BoxShadow(
                                color: Colors.black.withOpacity(0.25),
                                blurRadius: 6,
                                offset: const Offset(0, 3),
                              ),
                            ],
                          ),
                          child: const Icon(
                            Icons.trip_origin,
                            color: Colors.white,
                            size: 14,
                          ),
                        ),
                        Container(
                          width: 2.5,
                          height: 10,
                          color: const Color(0xFF2E7D32),
                        ),
                      ],
                    ),
                  ),
                // Route end pin
                if (_routeMode && _routeEnd != null)
                  Marker(
                    point: _routeEnd!,
                    width: 36,
                    height: 44,
                    alignment: Alignment.bottomCenter,
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Container(
                          width: 30,
                          height: 30,
                          decoration: BoxDecoration(
                            color: const Color(0xFFE53935),
                            shape: BoxShape.circle,
                            border: Border.all(color: Colors.white, width: 2.5),
                            boxShadow: [
                              BoxShadow(
                                color: Colors.black.withOpacity(0.25),
                                blurRadius: 6,
                                offset: const Offset(0, 3),
                              ),
                            ],
                          ),
                          child: const Icon(
                            Icons.flag,
                            color: Colors.white,
                            size: 14,
                          ),
                        ),
                        Container(
                          width: 2.5,
                          height: 10,
                          color: const Color(0xFFE53935),
                        ),
                      ],
                    ),
                  ),
              ]),
            ],
          ),

          // ── Floating search + navigate bar ──────────────────────────────
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  _buildFloatingSearchBar(),
                  if (_searchResults.isNotEmpty ||
                      (_searchActive && _editingStart && _userLocation != null))
                    const SizedBox(height: 8),
                  if (_searchResults.isNotEmpty ||
                      (_searchActive && _editingStart && _userLocation != null))
                    _buildSearchResultsList(),
                ],
              ),
            ),
          ),

          // ── Loading chip ────────────────────────────────────────────────
          if (_loading)
            const Positioned(
              top: 120,
              left: 0,
              right: 0,
              child: Center(child: _LoadingChip()),
            ),

          // ── Error banner ────────────────────────────────────────────────
          if (_errorMessage != null)
            Positioned(
              top: 120,
              left: 20,
              right: 20,
              child: _buildErrorBanner(_errorMessage!),
            ),

          // ── Route info card ─────────────────────────────────────────────
          _buildRouteInfoCard(),

          // ── Navigate button (when NOT in route mode) ────────────────────
          if (!_routeMode)
            Positioned(
              bottom: 192,
              right: 20,
              child: GestureDetector(
                onTap: _enterRouteMode,
                child: Container(
                  width: 48,
                  height: 48,
                  decoration: BoxDecoration(
                    color: Colors.white,
                    shape: BoxShape.circle,
                    boxShadow: [
                      BoxShadow(
                        color: Colors.black.withOpacity(0.15),
                        blurRadius: 12,
                        offset: const Offset(0, 4),
                      ),
                    ],
                  ),
                  child: const Icon(
                    Icons.directions,
                    color: AppColors.primary,
                    size: 24,
                  ),
                ),
              ),
            ),

          // ── Report FAB ──────────────────────────────────────────────────
          _buildReportFAB(),
        ],
      ),
    );
  }

  // ── Floating search bar ───────────────────────────────────────────────────

  Widget _buildFloatingSearchBar() {
    // Route mode idle: show expanded or compact panel
    if (_routeMode && !_searchActive) {
      return _routePanelExpanded
          ? _buildRouteInputPanel()
          : _buildCompactRoutePanel();
    }

    // Normal search bar (or active search inside route mode)
    return Material(
      elevation: 6,
      shadowColor: Colors.black26,
      borderRadius: BorderRadius.circular(16),
      color: Colors.white,
      child: Row(
        children: [
          IconButton(
            icon: const Icon(Icons.arrow_back, color: AppColors.primary),
            onPressed: _routeMode ? () {
              _closeSearch();
              setState(() => _editingStart = false);
            } : _closeSearch,
          ),
          Expanded(
            child: TextField(
              controller: _searchController,
              focusNode: _searchFocus,
              autofocus: true,
              onChanged: _onSearchChanged,
              textInputAction: TextInputAction.search,
              onSubmitted: _search,
              style: const TextStyle(fontSize: 15, color: AppColors.onSurface),
              decoration: InputDecoration(
                hintText: _routeMode
                    ? (_editingStart ? 'Search starting point…' : 'Search destination…')
                    : 'Search for a place…',
                hintStyle: const TextStyle(color: AppColors.onSurfaceVariant),
                border: InputBorder.none,
                contentPadding: const EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: 14,
                ),
              ),
            ),
          ),
          if (_searchLoading)
            const Padding(
              padding: EdgeInsets.symmetric(horizontal: 12),
              child: SizedBox(
                width: 18,
                height: 18,
                child: CircularProgressIndicator(
                  strokeWidth: 2,
                  color: AppColors.primary,
                ),
              ),
            )
          else if (_searchController.text.isNotEmpty)
            IconButton(
              icon: const Icon(Icons.clear,
                  color: AppColors.onSurfaceVariant, size: 20),
              onPressed: () {
                _searchController.clear();
                setState(() => _searchResults = []);
              },
            )
          else
            const SizedBox(width: 8),
        ],
      ),
    );
  }

  Widget _buildCompactRoutePanel() {
    final fromLabel = _routeStartLabel.isNotEmpty ? _routeStartLabel : 'Current Location';
    final toLabel = _routeEndLabel.isNotEmpty ? _routeEndLabel : '…';
    return Material(
      elevation: 4,
      shadowColor: Colors.black26,
      borderRadius: BorderRadius.circular(14),
      color: Colors.white,
      child: InkWell(
        onTap: () => setState(() => _routePanelExpanded = true),
        borderRadius: BorderRadius.circular(14),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 11),
          child: Row(
            children: [
              const Icon(Icons.route, color: AppColors.primary, size: 16),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  '$fromLabel  →  $toLabel',
                  style: const TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                    color: AppColors.onSurface,
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              const SizedBox(width: 6),
              const Icon(Icons.expand_more,
                  color: AppColors.onSurfaceVariant, size: 18),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildRouteInputPanel() {
    return Material(
      elevation: 6,
      shadowColor: Colors.black26,
      borderRadius: BorderRadius.circular(16),
      color: Colors.white,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 6),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            // From row
            _buildRouteField(
              icon: Icons.radio_button_checked,
              iconColor: const Color(0xFF1A73E8),
              label: _routeStartLabel,
              hint: 'Starting point',
              isSet: true,
              onTap: () {
                setState(() => _editingStart = true);
                _openSearch();
              },
            ),
            // Divider + swap
            Row(
              children: [
                const SizedBox(width: 52),
                Expanded(
                  child: Divider(height: 1, color: Colors.grey[200]),
                ),
                SizedBox(
                  width: 40,
                  height: 32,
                  child: IconButton(
                    padding: EdgeInsets.zero,
                    icon: const Icon(Icons.swap_vert,
                        size: 18, color: AppColors.onSurfaceVariant),
                    onPressed: _routeEnd != null ? _swapRoutePoints : null,
                  ),
                ),
                const SizedBox(width: 4),
              ],
            ),
            // To row
            _buildRouteField(
              icon: Icons.location_on,
              iconColor: const Color(0xFFE53935),
              label: _routeEndLabel.isNotEmpty ? _routeEndLabel : '',
              hint: 'Choose destination',
              isSet: _routeEndLabel.isNotEmpty,
              onTap: () {
                setState(() => _editingStart = false);
                _openSearch();
              },
            ),
            // Cancel button
            Padding(
              padding: const EdgeInsets.fromLTRB(12, 4, 12, 2),
              child: GestureDetector(
                onTap: _cancelRoute,
                child: Row(
                  children: [
                    const SizedBox(width: 36),
                    Text(
                      'Cancel route',
                      style: TextStyle(
                        fontSize: 12,
                        color: Colors.grey[500],
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildRouteField({
    required IconData icon,
    required Color iconColor,
    required String label,
    required String hint,
    required bool isSet,
    required VoidCallback onTap,
  }) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
        child: Row(
          children: [
            Icon(icon, color: iconColor, size: 18),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                isSet ? label : hint,
                style: TextStyle(
                  fontSize: 14,
                  color: isSet ? AppColors.onSurface : AppColors.onSurfaceVariant,
                  fontWeight: isSet ? FontWeight.w500 : FontWeight.w400,
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ),
            if (isSet && label.isNotEmpty && label != 'Current Location')
              GestureDetector(
                onTap: onTap,
                child: const Icon(Icons.edit_outlined,
                    size: 14, color: AppColors.onSurfaceVariant),
              ),
          ],
        ),
      ),
    );
  }

  // ── Search results ────────────────────────────────────────────────────────

  Widget _buildSearchResultsList() {
    final showCurrentLocation = _searchActive && _editingStart && _userLocation != null;
    final totalItems = _searchResults.length + (showCurrentLocation ? 1 : 0);
    return Material(
      elevation: 6,
      shadowColor: Colors.black26,
      borderRadius: BorderRadius.circular(16),
      color: Colors.white,
      child: ListView.separated(
        padding: const EdgeInsets.symmetric(vertical: 4),
        shrinkWrap: true,
        physics: const NeverScrollableScrollPhysics(),
        itemCount: totalItems,
        separatorBuilder: (_, __) =>
            const Divider(height: 1, indent: 52),
        itemBuilder: (context, i) {
          // First row: current location shortcut
          if (showCurrentLocation && i == 0) {
            return ListTile(
              leading: Container(
                width: 34,
                height: 34,
                decoration: const BoxDecoration(
                  color: Color(0x261A73E8),
                  shape: BoxShape.circle,
                ),
                child: const Icon(
                  Icons.my_location,
                  color: Color(0xFF1A73E8),
                  size: 18,
                ),
              ),
              title: const Text(
                'Current Location',
                style: TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
                  color: AppColors.onSurface,
                ),
              ),
              subtitle: const Text(
                'Use your GPS location',
                style: TextStyle(
                  fontSize: 11,
                  color: AppColors.onSurfaceVariant,
                ),
              ),
              onTap: () {
                setState(() {
                  _searchActive = false;
                  _searchResults = [];
                  _searchController.clear();
                  _routeStart = null;
                  _routeStartLabel = 'Current Location';
                  _editingStart = false;
                });
                _searchFocus.unfocus();
                if (_routeEnd != null) _fetchRoute(_routeEnd!);
              },
            );
          }
          final place = _searchResults[showCurrentLocation ? i - 1 : i];
          return ListTile(
            leading: Container(
              width: 34,
              height: 34,
              decoration: BoxDecoration(
                color: (_routeMode
                        ? const Color(0xFF1A73E8)
                        : AppColors.primary)
                    .withOpacity(0.1),
                shape: BoxShape.circle,
              ),
              child: Icon(
                _routeMode
                    ? Icons.flag_outlined
                    : Icons.location_on_outlined,
                color: _routeMode
                    ? const Color(0xFF1A73E8)
                    : AppColors.primary,
                size: 18,
              ),
            ),
            title: Text(
              place.displayName.split(',').first,
              style: const TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w600,
                color: AppColors.onSurface,
              ),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
            subtitle: Text(
              place.displayName,
              style: const TextStyle(
                fontSize: 11,
                color: AppColors.onSurfaceVariant,
              ),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
            onTap: () => _selectPlace(place),
          );
        },
      ),
    );
  }

  // ── Route info card ───────────────────────────────────────────────────────

  Widget _buildRouteInfoCard() {
    if (!_routeMode) return const SizedBox.shrink();

    if (_routeLoading) {
      return const Positioned(
        bottom: 100,
        left: 0,
        right: 0,
        child: Center(child: _LoadingChip()),
      );
    }

    if (_routeError != null) {
      return Positioned(
        bottom: 100,
        left: 20,
        right: 20,
        child: Container(
          padding:
              const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          decoration: BoxDecoration(
            color: const Color(0xFFF95630).withOpacity(0.92),
            borderRadius: BorderRadius.circular(14),
          ),
          child: Row(
            children: [
              const Icon(Icons.error_outline, color: Colors.white, size: 18),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  _routeError!,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 12,
                    fontWeight: FontWeight.w500,
                  ),
                ),
              ),
              GestureDetector(
                onTap: _cancelRoute,
                child: const Icon(Icons.close, color: Colors.white, size: 18),
              ),
            ],
          ),
        ),
      );
    }

    if (_routes.isEmpty) return const SizedBox.shrink();

    return Positioned(
      bottom: 100,
      left: 16,
      right: 16,
      child: Container(
        padding: const EdgeInsets.fromLTRB(16, 14, 16, 16),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(20),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(0.12),
              blurRadius: 20,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            // Header
            Row(
              children: [
                const Icon(Icons.route, color: AppColors.primary, size: 18),
                const SizedBox(width: 8),
                const Expanded(
                  child: Text(
                    'Route Options',
                    style: TextStyle(
                      fontFamily: 'Plus Jakarta Sans',
                      fontWeight: FontWeight.w700,
                      fontSize: 15,
                      color: AppColors.onSurface,
                    ),
                  ),
                ),
                GestureDetector(
                  onTap: _cancelRoute,
                  child: const Icon(Icons.close,
                      color: AppColors.onSurfaceVariant, size: 20),
                ),
              ],
            ),
            const SizedBox(height: 12),
            // One row per route
            ..._routes.asMap().entries.map((entry) {
              final i = entry.key;
              final r = entry.value;
              final selected = i == _selectedRouteIdx;
              return Padding(
                padding: EdgeInsets.only(top: i == 0 ? 0 : 10),
                child: GestureDetector(
                  onTap: () => setState(() => _selectedRouteIdx = i),
                  child: AnimatedContainer(
                    duration: const Duration(milliseconds: 200),
                    padding: const EdgeInsets.symmetric(
                        horizontal: 14, vertical: 12),
                    decoration: BoxDecoration(
                      color: selected
                          ? r.color.withOpacity(0.12)
                          : Colors.grey.withOpacity(0.06),
                      borderRadius: BorderRadius.circular(14),
                      border: Border.all(
                        color: selected
                            ? r.color
                            : Colors.grey.withOpacity(0.2),
                        width: selected ? 2.0 : 1.0,
                      ),
                    ),
                    child: Row(
                      children: [
                        Container(
                          width: 12,
                          height: 12,
                          decoration: BoxDecoration(
                            color: selected
                                ? r.color
                                : r.color.withOpacity(0.35),
                            shape: BoxShape.circle,
                          ),
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                r.label,
                                style: TextStyle(
                                  fontFamily: 'Plus Jakarta Sans',
                                  fontWeight: FontWeight.w700,
                                  fontSize: 13,
                                  color: selected
                                      ? r.color
                                      : AppColors.onSurfaceVariant,
                                ),
                              ),
                              const SizedBox(height: 2),
                              Text(
                                '${r.distanceLabel}  ·  ${r.durationLabel}',
                                style: TextStyle(
                                  fontSize: 12,
                                  fontWeight: FontWeight.w500,
                                  color: selected
                                      ? AppColors.onSurface
                                      : AppColors.onSurfaceVariant
                                          .withOpacity(0.6),
                                ),
                              ),
                            ],
                          ),
                        ),
                        if (r.hasObstacles)
                          Padding(
                            padding: const EdgeInsets.only(left: 6),
                            child: Icon(Icons.warning_amber_rounded,
                                color: selected
                                    ? const Color(0xFFFFA726)
                                    : const Color(0xFFFFA726).withOpacity(0.4),
                                size: 18),
                          ),
                        if (selected)
                          Padding(
                            padding: const EdgeInsets.only(left: 8),
                            child: Icon(Icons.check_circle,
                                color: r.color, size: 18),
                          ),
                      ],
                    ),
                  ),
                ),
              );
            }),
          ],
        ),
      ),
    );
  }

  // ── Error banner ──────────────────────────────────────────────────────────

  Widget _buildErrorBanner(String error) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: BoxDecoration(
        color: const Color(0xFFF95630).withOpacity(0.92),
        borderRadius: BorderRadius.circular(14),
      ),
      child: Row(
        children: [
          const Icon(Icons.wifi_off, color: Colors.white, size: 18),
          const SizedBox(width: 10),
          const Expanded(
            child: Text(
              'Could not load reports. Check your connection.',
              style: TextStyle(
                color: Colors.white,
                fontSize: 12,
                fontWeight: FontWeight.w500,
              ),
            ),
          ),
          GestureDetector(
            onTap: _loadReports,
            child: const Icon(Icons.refresh, color: Colors.white, size: 18),
          ),
        ],
      ),
    );
  }

  // ── Login dialog ──────────────────────────────────────────────────────────

  void _showLoginRequiredDialog() {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        shape:
            RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: const Text(
          'Sign In Required',
          style: TextStyle(
            fontFamily: 'Plus Jakarta Sans',
            fontWeight: FontWeight.w700,
            color: AppColors.onSurface,
          ),
        ),
        content: const Text(
          'You need to log in to use this feature.',
          style: TextStyle(color: AppColors.onSurfaceVariant),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel',
                style: TextStyle(color: AppColors.outline)),
          ),
          TextButton(
            onPressed: () {
              Navigator.pop(ctx);
              Navigator.pushAndRemoveUntil(
                context,
                MaterialPageRoute(builder: (_) => const LoginScreen()),
                (r) => false,
              );
            },
            child: const Text(
              'Sign In',
              style: TextStyle(
                  color: AppColors.primary, fontWeight: FontWeight.w700),
            ),
          ),
        ],
      ),
    );
  }

  // ── Report FAB ────────────────────────────────────────────────────────────

  Widget _buildReportFAB() {
    if (_routeMode) return const SizedBox.shrink();
    return Positioned(
      bottom: 120,
      left: 20,
      right: 20,
      child: GestureDetector(
        onTap: () {
          if (!context.read<AuthService>().isAuthenticated) {
            _showLoginRequiredDialog();
            return;
          }
          Navigator.push(
            context,
            MaterialPageRoute(builder: (_) => const MakeReportScreen()),
          );
        },
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: 18),
          decoration: BoxDecoration(
            gradient: const LinearGradient(
              begin: Alignment.topCenter,
              end: Alignment.bottomCenter,
              colors: [AppColors.primary, AppColors.primaryDim],
            ),
            borderRadius: BorderRadius.circular(999),
            boxShadow: [
              BoxShadow(
                color: AppColors.primary.withOpacity(0.3),
                blurRadius: 32,
                offset: const Offset(0, 12),
              ),
            ],
          ),
          child: const Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(Icons.add_circle, color: AppColors.onPrimary, size: 26),
              SizedBox(width: 10),
              Text(
                'Report an Issue',
                style: TextStyle(
                  fontFamily: 'Plus Jakarta Sans',
                  fontWeight: FontWeight.w700,
                  fontSize: 17,
                  color: AppColors.onPrimary,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

// ── Route data model ──────────────────────────────────────────────────────────

class _RouteData {
  final String label;
  final double distanceMeters;
  final double durationSeconds;
  final bool hasObstacles;
  final List<LatLng> points;

  const _RouteData({
    required this.label,
    required this.distanceMeters,
    required this.durationSeconds,
    required this.hasObstacles,
    required this.points,
  });

  factory _RouteData.fromJson(Map<String, dynamic> json, List<LatLng> points) {
    return _RouteData(
      label: json['routeLabel'] as String? ?? 'Route',
      distanceMeters: (json['distanceMeters'] as num?)?.toDouble() ?? 0,
      durationSeconds: (json['durationSeconds'] as num?)?.toDouble() ?? 0,
      hasObstacles: json['hasObstacles'] as bool? ?? false,
      points: points,
    );
  }

  /// Vivid, distinct polyline color per route type.
  Color get color {
    if (label.contains('Accessible')) return const Color(0xFF1565C0); // deep blue
    if (label.contains('Wheelchair')) return const Color(0xFF6A1B9A); // deep purple
    if (label.contains('Ramp'))       return const Color(0xFF00695C); // deep teal
    // Fastest route: amber-orange if has obstacles, vivid green if clear
    return hasObstacles ? const Color(0xFFE65100) : const Color(0xFF2E7D32);
  }

  String get distanceLabel {
    if (distanceMeters < 1000) return '${distanceMeters.round()} m';
    return '${(distanceMeters / 1000).toStringAsFixed(1)} km';
  }

  String get durationLabel {
    final mins = (durationSeconds / 60).round();
    if (mins < 60) return '$mins min';
    return '${mins ~/ 60}h ${mins % 60}min';
  }
}

// ── Loading chip ──────────────────────────────────────────────────────────────

class _LoadingChip extends StatelessWidget {
  const _LoadingChip();

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
      decoration: BoxDecoration(
        color: Colors.white.withOpacity(0.92),
        borderRadius: BorderRadius.circular(999),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.08),
            blurRadius: 12,
          ),
        ],
      ),
      child: const Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          SizedBox(
            width: 14,
            height: 14,
            child: CircularProgressIndicator(
              strokeWidth: 2,
              color: AppColors.primary,
            ),
          ),
          SizedBox(width: 10),
          Text(
            'Loading…',
            style: TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w500,
              color: AppColors.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
  }
}

// ── Nominatim result model ────────────────────────────────────────────────────

class _Place {
  final String displayName;
  final double lat;
  final double lon;

  const _Place({
    required this.displayName,
    required this.lat,
    required this.lon,
  });

  factory _Place.fromJson(Map<String, dynamic> json) {
    return _Place(
      displayName: json['display_name'] as String,
      lat: double.parse(json['lat'] as String),
      lon: double.parse(json['lon'] as String),
    );
  }
}
