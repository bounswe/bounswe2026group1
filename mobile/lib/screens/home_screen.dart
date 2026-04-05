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
      final pos = await Geolocator.getCurrentPosition(
        locationSettings: const LocationSettings(accuracy: LocationAccuracy.high),
      );
      if (!mounted) return;
      _mapController.move(LatLng(pos.latitude, pos.longitude), 15);
    } catch (_) {
      // Permission denied or location unavailable — stay at default
    }
  }

  @override
  void dispose() {
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
      // Network error — leave results empty
    } finally {
      if (mounted) setState(() => _searchLoading = false);
    }
  }

  void _selectPlace(_Place place) {
    _mapController.move(LatLng(place.lat, place.lon), 15);
    setState(() {
      _searchActive = false;
      _searchResults = [];
      _searchController.clear();
    });
    _searchFocus.unfocus();
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
    });
    _searchFocus.unfocus();
  }

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
      if (reports.isNotEmpty) {
        _mapController.move(_centerOf(reports), 14);
      }
    } catch (e) {
      if (mounted) setState(() {
        _loading = false;
        _errorMessage = e.toString();
      });
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
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
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

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Stack(
        children: [
          // ── Full-screen map ───────────────────────────────────────────────
          FlutterMap(
            mapController: _mapController,
            options: const MapOptions(
              initialCenter: _defaultCenter,
              initialZoom: 14,
            ),
            children: [
              TileLayer(
                urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
                userAgentPackageName: 'com.bounswe2026group1.mapcess',
              ),
              MarkerLayer(markers: _markers),
            ],
          ),
          // ── Floating search bar ───────────────────────────────────────────
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  _buildFloatingSearchBar(),
                  if (_searchActive && _searchResults.isNotEmpty)
                    const SizedBox(height: 8),
                  if (_searchActive && _searchResults.isNotEmpty)
                    _buildSearchResultsList(),
                ],
              ),
            ),
          ),
          // ── Loading chip ─────────────────────────────────────────────────
          if (_loading)
            const Positioned(
              top: 120,
              left: 0,
              right: 0,
              child: Center(child: _LoadingChip()),
            ),
          // ── Error banner ─────────────────────────────────────────────────
          if (_errorMessage != null)
            Positioned(
              top: 120,
              left: 20,
              right: 20,
              child: _buildErrorBanner(_errorMessage!),
            ),
          // ── Report FAB ────────────────────────────────────────────────────
          _buildReportFAB(),
        ],
      ),
    );
  }

  // ─── Floating search bar (on-map) ─────────────────────────────────────────

  Widget _buildFloatingSearchBar() {
    return Material(
      elevation: 6,
      shadowColor: Colors.black26,
      borderRadius: BorderRadius.circular(16),
      color: Colors.white,
      child: Row(
        children: [
          if (_searchActive)
            IconButton(
              icon: const Icon(Icons.arrow_back, color: AppColors.primary),
              onPressed: _closeSearch,
            )
          else
            const Padding(
              padding: EdgeInsets.only(left: 14),
              child: Icon(Icons.search, color: AppColors.primary, size: 22),
            ),
          Expanded(
            child: TextField(
              controller: _searchController,
              focusNode: _searchFocus,
              onTap: () {
                if (!_searchActive) _openSearch();
              },
              onChanged: _onSearchChanged,
              textInputAction: TextInputAction.search,
              onSubmitted: _search,
              style: const TextStyle(fontSize: 15, color: AppColors.onSurface),
              decoration: InputDecoration(
                hintText: 'Search for a place…',
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
          else if (_searchActive && _searchController.text.isNotEmpty)
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

  // ─── Search results list (inline below search bar) ─────────────────────────

  Widget _buildSearchResultsList() {
    return Material(
      elevation: 6,
      shadowColor: Colors.black26,
      borderRadius: BorderRadius.circular(16),
      color: Colors.white,
      child: ListView.separated(
        padding: const EdgeInsets.symmetric(vertical: 4),
        shrinkWrap: true,
        physics: const NeverScrollableScrollPhysics(),
        itemCount: _searchResults.length,
        separatorBuilder: (_, __) => const Divider(height: 1, indent: 52),
        itemBuilder: (context, i) {
          final place = _searchResults[i];
          return ListTile(
            leading: Container(
              width: 34,
              height: 34,
              decoration: BoxDecoration(
                color: AppColors.primary.withOpacity(0.1),
                shape: BoxShape.circle,
              ),
              child: const Icon(
                Icons.location_on_outlined,
                color: AppColors.primary,
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

  // ─── Error banner ──────────────────────────────────────────────────────────

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

  void _showLoginRequiredDialog() {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
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
            child: const Text('Cancel', style: TextStyle(color: AppColors.outline)),
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
              style: TextStyle(color: AppColors.primary, fontWeight: FontWeight.w700),
            ),
          ),
        ],
      ),
    );
  }

  // ─── Report FAB ────────────────────────────────────────────────────────────

  Widget _buildReportFAB() {
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

// ─── Loading chip ──────────────────────────────────────────────────────────────

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
            'Loading reports…',
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

// ─── Nominatim result model ────────────────────────────────────────────────────

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
