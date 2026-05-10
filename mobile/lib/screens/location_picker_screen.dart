import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:geolocator/geolocator.dart';
import 'package:latlong2/latlong.dart';

import '../theme/app_colors.dart';

/// Full-screen map picker that returns a [LatLng] (or null if cancelled).
/// Tap anywhere on the map to drop the pin, or use the "My location" FAB to
/// jump to the device GPS. Mirrors the picker pattern in make_report_screen
/// but is decoupled from the report-creation flow so the feed can reuse it.
class LocationPickerScreen extends StatefulWidget {
  final LatLng? initialLocation;
  final double? radiusKm;

  const LocationPickerScreen({
    super.key,
    this.initialLocation,
    this.radiusKm,
  });

  @override
  State<LocationPickerScreen> createState() => _LocationPickerScreenState();
}

class _LocationPickerScreenState extends State<LocationPickerScreen> {
  // Bogazici North Campus — sensible default when the user has never granted
  // location and there's no prior pin.
  static const _defaultCenter = LatLng(41.0857, 29.0510);

  late final MapController _mapController;
  late LatLng _pin;
  bool _locating = false;

  @override
  void initState() {
    super.initState();
    _mapController = MapController();
    _pin = widget.initialLocation ?? _defaultCenter;
  }

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
      final loc = LatLng(pos.latitude, pos.longitude);
      setState(() => _pin = loc);
      _mapController.move(loc, 15);
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Could not get current location.')),
      );
    } finally {
      if (mounted) setState(() => _locating = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final radiusMeters = (widget.radiusKm ?? 0) * 1000;
    return Scaffold(
      body: Stack(
        children: [
          FlutterMap(
            mapController: _mapController,
            options: MapOptions(
              initialCenter: _pin,
              initialZoom: 14,
              onTap: (_, latLng) => setState(() => _pin = latLng),
            ),
            children: [
              TileLayer(
                urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
                userAgentPackageName: 'com.bounswe2026group1.mapcess',
              ),
              if (radiusMeters > 0)
                CircleLayer(
                  circles: [
                    CircleMarker(
                      point: _pin,
                      radius: radiusMeters,
                      useRadiusInMeter: true,
                      color: AppColors.primary.withValues(alpha: 0.15),
                      borderColor: AppColors.primary,
                      borderStrokeWidth: 2,
                    ),
                  ],
                ),
              MarkerLayer(
                markers: [
                  Marker(
                    point: _pin,
                    width: 44,
                    height: 44,
                    alignment: Alignment.topCenter,
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
          // Top bar — back + title
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(8, 8, 16, 0),
              child: Row(
                children: [
                  _RoundIconButton(
                    icon: Icons.arrow_back,
                    onTap: () => Navigator.pop(context),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 14, vertical: 10),
                      decoration: BoxDecoration(
                        color: AppColors.surfaceContainerLowest
                            .withValues(alpha: 0.95),
                        borderRadius: BorderRadius.circular(999),
                        boxShadow: [
                          BoxShadow(
                            color: AppColors.shadow,
                            blurRadius: 12,
                            offset: const Offset(0, 2),
                          ),
                        ],
                      ),
                      child: Row(
                        children: [
                          Icon(Icons.tap_and_play,
                              size: 16, color: AppColors.primary),
                          const SizedBox(width: 6),
                          Expanded(
                            child: Text(
                              'Tap on the map to choose a location',
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: TextStyle(
                                fontSize: 12,
                                fontWeight: FontWeight.w600,
                                color: AppColors.onSurface,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
          // FAB — use my location
          Positioned(
            right: 16,
            bottom: 120,
            child: FloatingActionButton(
              backgroundColor: AppColors.surfaceContainerLowest,
              foregroundColor: AppColors.primary,
              onPressed: _useMyLocation,
              tooltip: 'Use my location',
              child: _locating
                  ? SizedBox(
                      width: 22,
                      height: 22,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        color: AppColors.primary,
                      ),
                    )
                  : const Icon(Icons.my_location),
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
                padding: const EdgeInsets.symmetric(vertical: 16),
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
                        color: AppColors.onPrimary, size: 20),
                    const SizedBox(width: 10),
                    Text(
                      'Confirm Location',
                      style: TextStyle(
                        fontFamily: 'Plus Jakarta Sans',
                        fontWeight: FontWeight.w700,
                        fontSize: 16,
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
}

class _RoundIconButton extends StatelessWidget {
  final IconData icon;
  final VoidCallback onTap;

  const _RoundIconButton({required this.icon, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Material(
      color: AppColors.surfaceContainerLowest.withValues(alpha: 0.95),
      shape: const CircleBorder(),
      elevation: 2,
      child: InkWell(
        customBorder: const CircleBorder(),
        onTap: onTap,
        child: SizedBox(
          width: 40,
          height: 40,
          child: Icon(icon, color: AppColors.onSurface, size: 20),
        ),
      ),
    );
  }
}
