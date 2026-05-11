import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:http/http.dart' as http;
import 'package:latlong2/latlong.dart';

import '../theme/app_colors.dart';

/// Full-screen pin picker shared by report create + edit flows. Tap the map
/// to drop the pin, or use the search bar to jump to a Nominatim hit. Pops
/// with the chosen [LatLng] when the user taps "Confirm Location"; pops
/// with `null` when they dismiss with the × button.
///
/// Kept in its own file (not nested inside a screen) so both `MakeReport`
/// and `EditReport` reach for the same UI — the picker is what the user
/// sees when they tap the zoom affordance on the inline map preview.
class ReportLocationPickerScreen extends StatefulWidget {
  final LatLng initial;
  const ReportLocationPickerScreen({super.key, required this.initial});

  @override
  State<ReportLocationPickerScreen> createState() =>
      _ReportLocationPickerScreenState();
}

class _ReportLocationPickerScreenState
    extends State<ReportLocationPickerScreen> {
  late LatLng _pin;
  final MapController _mapController = MapController();
  final TextEditingController _searchController = TextEditingController();
  final FocusNode _searchFocus = FocusNode();
  List<_Place> _results = [];
  bool _searchLoading = false;
  bool _searchActive = false;
  Timer? _debounce;

  @override
  void initState() {
    super.initState();
    _pin = widget.initial;
  }

  @override
  void dispose() {
    _mapController.dispose();
    _searchController.dispose();
    _searchFocus.dispose();
    _debounce?.cancel();
    super.dispose();
  }

  void _onChanged(String query) {
    _debounce?.cancel();
    if (query.trim().isEmpty) {
      setState(() => _results = []);
      return;
    }
    _debounce = Timer(
      const Duration(milliseconds: 500),
      () => _search(query),
    );
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
          _results = list
              .map((e) => _Place.fromJson(e as Map<String, dynamic>))
              .toList();
        });
      }
    } catch (_) {
      // Best-effort — surface no results rather than an error toast since
      // the user can still drop the pin manually.
    } finally {
      if (mounted) setState(() => _searchLoading = false);
    }
  }

  void _selectPlace(_Place place) {
    final latLng = LatLng(place.lat, place.lon);
    setState(() {
      _pin = latLng;
      _results = [];
      _searchActive = false;
      _searchController.clear();
    });
    _searchFocus.unfocus();
    _mapController.move(latLng, 15);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Stack(
        children: [
          // Full-screen map
          FlutterMap(
            mapController: _mapController,
            options: MapOptions(
              initialCenter: _pin,
              initialZoom: 15,
              onTap: (_, latLng) => setState(() => _pin = latLng),
            ),
            children: [
              TileLayer(
                urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
                userAgentPackageName: 'com.bounswe2026group1.mapcess',
              ),
              MarkerLayer(
                markers: [
                  Marker(
                    point: _pin,
                    child: Icon(
                      Icons.location_on,
                      color: AppColors.primary,
                      size: 40,
                    ),
                  ),
                ],
              ),
            ],
          ),
          // Search bar + results
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  _buildSearchBar(),
                  if (_searchActive && _results.isNotEmpty)
                    const SizedBox(height: 8),
                  if (_searchActive && _results.isNotEmpty)
                    _buildResultsList(),
                ],
              ),
            ),
          ),
          // Confirm button
          Positioned(
            bottom: 32,
            left: 24,
            right: 24,
            child: GestureDetector(
              onTap: () => Navigator.pop(context, _pin),
              child: Container(
                padding: const EdgeInsets.symmetric(vertical: 18),
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    colors: [AppColors.primary, AppColors.primaryDim],
                  ),
                  borderRadius: BorderRadius.circular(999),
                  boxShadow: [
                    BoxShadow(
                      color: AppColors.primary.withValues(alpha: 0.35),
                      blurRadius: 24,
                      offset: const Offset(0, 8),
                    ),
                  ],
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(Icons.check_circle_outline,
                        color: AppColors.onPrimary, size: 22),
                    const SizedBox(width: 10),
                    Text(
                      'Confirm Location',
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
          ),
        ],
      ),
    );
  }

  Widget _buildSearchBar() {
    return Material(
      elevation: 6,
      shadowColor: AppColors.shadow,
      borderRadius: BorderRadius.circular(16),
      color: AppColors.cardSurface,
      child: Row(
        children: [
          if (_searchActive)
            IconButton(
              icon: Icon(Icons.arrow_back, color: AppColors.primary),
              onPressed: () {
                setState(() {
                  _searchActive = false;
                  _results = [];
                  _searchController.clear();
                });
                _searchFocus.unfocus();
              },
            )
          else
            IconButton(
              icon: Icon(Icons.close, color: AppColors.primary),
              onPressed: () => Navigator.pop(context),
            ),
          Expanded(
            child: TextField(
              controller: _searchController,
              focusNode: _searchFocus,
              onTap: () {
                if (!_searchActive) setState(() => _searchActive = true);
              },
              onChanged: _onChanged,
              textInputAction: TextInputAction.search,
              onSubmitted: _search,
              style: TextStyle(fontSize: 15, color: AppColors.onSurface),
              decoration: InputDecoration(
                hintText: 'Search for a place…',
                hintStyle: TextStyle(color: AppColors.onSurfaceVariant),
                border: InputBorder.none,
                contentPadding: const EdgeInsets.symmetric(
                    horizontal: 8, vertical: 14),
              ),
            ),
          ),
          if (_searchLoading)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12),
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
              icon: Icon(Icons.clear,
                  color: AppColors.onSurfaceVariant, size: 20),
              onPressed: () {
                _searchController.clear();
                setState(() => _results = []);
              },
            )
          else
            const SizedBox(width: 8),
        ],
      ),
    );
  }

  Widget _buildResultsList() {
    return Material(
      elevation: 6,
      shadowColor: AppColors.shadow,
      borderRadius: BorderRadius.circular(16),
      color: AppColors.cardSurface,
      child: ListView.separated(
        padding: const EdgeInsets.symmetric(vertical: 4),
        shrinkWrap: true,
        physics: const NeverScrollableScrollPhysics(),
        itemCount: _results.length,
        separatorBuilder: (_, __) => const Divider(height: 1, indent: 52),
        itemBuilder: (context, i) {
          final place = _results[i];
          return ListTile(
            leading: Container(
              width: 34,
              height: 34,
              decoration: BoxDecoration(
                color: AppColors.primary.withValues(alpha: 0.1),
                shape: BoxShape.circle,
              ),
              child: Icon(Icons.location_on_outlined,
                  color: AppColors.primary, size: 18),
            ),
            title: Text(
              place.displayName.split(',').first,
              style: TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
                  color: AppColors.onSurface),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
            subtitle: Text(
              place.displayName,
              style: TextStyle(
                  fontSize: 11, color: AppColors.onSurfaceVariant),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
            onTap: () => _selectPlace(place),
          );
        },
      ),
    );
  }
}

class _Place {
  final String displayName;
  final double lat;
  final double lon;

  const _Place({
    required this.displayName,
    required this.lat,
    required this.lon,
  });

  factory _Place.fromJson(Map<String, dynamic> json) => _Place(
        displayName: json['display_name'] as String,
        lat: double.parse(json['lat'] as String),
        lon: double.parse(json['lon'] as String),
      );
}
