import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:geolocator/geolocator.dart';
import 'package:http/http.dart' as http;
import 'package:image_picker/image_picker.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:video_compress/video_compress.dart';
import 'package:latlong2/latlong.dart';
import 'package:provider/provider.dart';
import '../theme/app_colors.dart';
import '../models/report_model.dart';
import '../services/auth_service.dart';
import '../services/api_service.dart';
import 'report_success_screen.dart';

// Fallback location used before GPS resolves: Mission District, San Francisco
const LatLng _defaultPin = LatLng(37.7599, -122.4148);

class MakeReportScreen extends StatefulWidget {
  const MakeReportScreen({super.key});

  @override
  State<MakeReportScreen> createState() => _MakeReportScreenState();
}

class _MakeReportScreenState extends State<MakeReportScreen> {
  final _descController = TextEditingController();
  ReportTag _selectedTag = ReportTag.other;
  File? _selectedMedia;
  bool _isVideo = false;
  bool _converting = false;
  bool _submitting = false;

  final _picker = ImagePicker();

  // ── Location state ─────────────────────────────────────────────────────────
  final MapController _mapController = MapController();
  LatLng _pinLocation = _defaultPin;

  // ── Location search state ──────────────────────────────────────────────────
  final TextEditingController _locationSearchController = TextEditingController();
  final FocusNode _locationSearchFocus = FocusNode();
  List<_Place> _locationResults = [];
  bool _locationSearchLoading = false;
  bool _locationSearchActive = false;
  Timer? _locationDebounce;

  @override
  void initState() {
    super.initState();
    _initLocation();
  }

  Future<void> _initLocation() async {
    try {
      var permission = await Geolocator.checkPermission();
      if (permission == LocationPermission.denied) {
        permission = await Geolocator.requestPermission();
      }
      if (permission == LocationPermission.denied ||
          permission == LocationPermission.deniedForever) return;

      final pos = await Geolocator.getCurrentPosition();
      if (!mounted) return;
      final latLng = LatLng(pos.latitude, pos.longitude);
      setState(() => _pinLocation = latLng);
      _mapController.move(latLng, 15);
    } catch (_) {
      // GPS unavailable — keep default pin
    }
  }

  void _movePin(LatLng pos) => setState(() => _pinLocation = pos);

  @override
  void dispose() {
    _descController.dispose();
    _mapController.dispose();
    _locationSearchController.dispose();
    _locationSearchFocus.dispose();
    _locationDebounce?.cancel();
    super.dispose();
  }

  // ─── Location search ───────────────────────────────────────────────────────

  void _onLocationSearchChanged(String query) {
    _locationDebounce?.cancel();
    if (query.trim().isEmpty) {
      setState(() => _locationResults = []);
      return;
    }
    _locationDebounce = Timer(
      const Duration(milliseconds: 500),
      () => _searchLocation(query),
    );
  }

  Future<void> _searchLocation(String query) async {
    setState(() => _locationSearchLoading = true);
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
          _locationResults = list
              .map((e) => _Place.fromJson(e as Map<String, dynamic>))
              .toList();
        });
      }
    } catch (_) {
      // Network error — leave results empty
    } finally {
      if (mounted) setState(() => _locationSearchLoading = false);
    }
  }

  void _selectLocation(_Place place) {
    final latLng = LatLng(place.lat, place.lon);
    setState(() {
      _pinLocation = latLng;
      _locationResults = [];
      _locationSearchActive = false;
      _locationSearchController.clear();
    });
    _locationSearchFocus.unfocus();
    _mapController.move(latLng, 15);
  }

  Future<void> _openFullScreenPicker() async {
    final result = await Navigator.push<LatLng>(
      context,
      MaterialPageRoute(
        fullscreenDialog: true,
        builder: (_) => _LocationPickerScreen(initial: _pinLocation),
      ),
    );
    if (result != null && mounted) {
      setState(() => _pinLocation = result);
      _mapController.move(result, 15);
    }
  }

  // ─── Media picking ─────────────────────────────────────────────────────────

  /// Returns true if the required permission is granted.
  /// Requests it if not yet determined; shows a Settings dialog if denied.
  Future<bool> _ensurePermission(Permission permission) async {
    var status = await permission.status;
    if (status.isGranted) return true;

    if (status.isDenied) {
      status = await permission.request();
      if (status.isGranted) return true;
    }

    if (status.isPermanentlyDenied || status.isDenied) {
      if (!mounted) return false;
      await showDialog<void>(
        context: context,
        builder: (ctx) => AlertDialog(
          title: const Text('Permission required'),
          content: const Text(
            'Camera access was denied. Please enable it in Settings to take photos or videos.',
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('Cancel'),
            ),
            TextButton(
              onPressed: () {
                Navigator.pop(ctx);
                openAppSettings();
              },
              child: const Text('Open Settings'),
            ),
          ],
        ),
      );
      return false;
    }

    return false;
  }

  Future<void> _pickImage(ImageSource source) async {
    if (source == ImageSource.camera) {
      final granted = await _ensurePermission(Permission.camera);
      if (!granted) return;
    }
    try {
      final picked = await _picker.pickImage(
        source: source,
        imageQuality: 80,
        maxWidth: 1080,
      );
      if (picked != null) {
        setState(() {
          _selectedMedia = File(picked.path);
          _isVideo = false;
        });
      }
    } catch (_) {}
  }

  Future<void> _pickVideo(ImageSource source) async {
    if (source == ImageSource.camera) {
      final camGranted = await _ensurePermission(Permission.camera);
      if (!camGranted) return;
      final micGranted = await _ensurePermission(Permission.microphone);
      if (!micGranted) return;
    }
    try {
      final picked = await _picker.pickVideo(source: source);
      if (picked == null) return;

      // Show placeholder immediately so the user sees feedback
      setState(() {
        _isVideo = true;
        _converting = true;
        _selectedMedia = null;
      });

      try {
        final info = await VideoCompress.compressVideo(
          picked.path,
          quality: VideoQuality.MediumQuality,
          deleteOrigin: false,
          includeAudio: true,
        );
        if (info?.file != null && mounted) {
          setState(() => _selectedMedia = info!.file!);
        } else if (mounted) {
          // Fallback: use original file as-is
          setState(() => _selectedMedia = File(picked.path));
        }
      } catch (_) {
        if (mounted) setState(() => _selectedMedia = File(picked.path));
      } finally {
        if (mounted) setState(() => _converting = false);
      }
    } catch (_) {
      if (mounted) setState(() { _isVideo = false; _converting = false; });
    }
  }

  void _showMediaOptions() {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (_) => Container(
        margin: const EdgeInsets.fromLTRB(16, 0, 16, 32),
        decoration: BoxDecoration(
          color: AppColors.surfaceContainerLowest,
          borderRadius: BorderRadius.circular(24),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const SizedBox(height: 12),
            Container(
              width: 36,
              height: 4,
              decoration: BoxDecoration(
                color: AppColors.outlineVariant,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
            const SizedBox(height: 16),
            _sheetOption(
              icon: Icons.camera_alt_outlined,
              label: 'Take Photo',
              onTap: () {
                Navigator.pop(context);
                _pickImage(ImageSource.camera);
              },
            ),
            _sheetOption(
              icon: Icons.photo_library_outlined,
              label: 'Choose Photo from Gallery',
              onTap: () {
                Navigator.pop(context);
                _pickImage(ImageSource.gallery);
              },
            ),
            _sheetOption(
              icon: Icons.videocam_outlined,
              label: 'Record Video',
              onTap: () {
                Navigator.pop(context);
                _pickVideo(ImageSource.camera);
              },
            ),
            _sheetOption(
              icon: Icons.video_library_outlined,
              label: 'Choose Video from Gallery',
              onTap: () {
                Navigator.pop(context);
                _pickVideo(ImageSource.gallery);
              },
            ),
            if (_selectedMedia != null)
              _sheetOption(
                icon: Icons.delete_outline,
                label: 'Remove Media',
                color: AppColors.errorStrong,
                onTap: () {
                  setState(() {
                    _selectedMedia = null;
                    _isVideo = false;
                  });
                  Navigator.pop(context);
                },
              ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
  }

  Widget _sheetOption({
    required IconData icon,
    required String label,
    required VoidCallback onTap,
    Color? color,
  }) {
    return ListTile(
      leading: Icon(icon, color: color ?? AppColors.onSurface),
      title: Text(
        label,
        style: TextStyle(
          fontWeight: FontWeight.w600,
          color: color ?? AppColors.onSurface,
        ),
      ),
      onTap: onTap,
    );
  }

  // ─── Submit ────────────────────────────────────────────────────────────────

  Future<void> _submit() async {
    final desc = _descController.text.trim();
    if (desc.length < 10) {
      _showError('Description must be at least 10 characters.');
      return;
    }

    setState(() => _submitting = true);
    try {
      final auth = context.read<AuthService>();
      final report = await auth.api.createReport(
        userId: auth.userId,
        latitude: _pinLocation.latitude,
        longitude: _pinLocation.longitude,
        description: desc,
        tag: _selectedTag,
      );

      // Upload media if selected
      if (_selectedMedia != null) {
        try {
          await auth.api.uploadMedia(report.reportId, _selectedMedia!);
        } catch (e) {
          // Media upload failed — report was created; show warning but continue
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text('Report posted, but media upload failed: $e'),
                duration: const Duration(seconds: 6),
              ),
            );
          }
        }
      }

      if (!mounted) return;
      Navigator.pushReplacement(
        context,
        MaterialPageRoute(
          builder: (_) => ReportSuccessScreen(report: report),
        ),
      );
    } on ApiException catch (e) {
      if (mounted) _showError(e.userMessage);
    } catch (_) {
      if (mounted) _showError('Failed to submit. Please try again.');
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  void _showError(String msg) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(msg),
        backgroundColor: AppColors.errorStrong,
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
    );
  }

  // ─── Build ─────────────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.surface,
      body: Column(
        children: [
          _buildTopBar(),
          Expanded(
            child: SingleChildScrollView(
              padding: const EdgeInsets.fromLTRB(20, 16, 20, 24),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _buildProgressHeader(),
                  const SizedBox(height: 24),
                  _buildImageSection(),
                  const SizedBox(height: 20),
                  _buildLocationCard(),
                  const SizedBox(height: 20),
                  _buildDescriptionField(),
                  const SizedBox(height: 20),
                  _buildCategorySelector(),
                  const SizedBox(height: 20),
                  _buildImpactCard(),
                  const SizedBox(height: 120),
                ],
              ),
            ),
          ),
        ],
      ),
      bottomSheet: _buildBottomBar(),
    );
  }

  // ─── Top bar ───────────────────────────────────────────────────────────────

  Widget _buildTopBar() {
    return SafeArea(
      bottom: false,
      child: Container(
        color: AppColors.surface.withOpacity(0.92),
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
        child: Row(
          children: [
            IconButton(
              icon: Icon(Icons.close, color: AppColors.primary),
              onPressed: () => Navigator.pop(context),
            ),
            const SizedBox(width: 4),
            Text(
              'Mapcess',
              style: TextStyle(
                fontFamily: 'Plus Jakarta Sans',
                fontWeight: FontWeight.w800,
                fontSize: 24,
                color: AppColors.primary,
                letterSpacing: -0.5,
              ),
            ),
            const Spacer(),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 5),
              decoration: BoxDecoration(
                color: AppColors.surfaceContainer,
                borderRadius: BorderRadius.circular(999),
              ),
              child: Text(
                'DRAFT',
                style: TextStyle(
                  fontSize: 10,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 1.2,
                  color: AppColors.secondary,
                ),
              ),
            ),
            const SizedBox(width: 8),
          ],
        ),
      ),
    );
  }

  // ─── Progress header ───────────────────────────────────────────────────────

  Widget _buildProgressHeader() {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'NEW REPORT',
                style: TextStyle(
                  fontSize: 10,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 1.5,
                  color: AppColors.primary,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                'Issue Details',
                style: TextStyle(
                  fontFamily: 'Plus Jakarta Sans',
                  fontWeight: FontWeight.w800,
                  fontSize: 28,
                  color: AppColors.onSurface,
                  letterSpacing: -0.5,
                ),
              ),
            ],
          ),
        ),
        Row(
          children: [
            _dot(true),
            const SizedBox(width: 4),
            _dot(true),
            const SizedBox(width: 4),
            _dot(false),
          ],
        ),
      ],
    );
  }

  Widget _dot(bool active) => AnimatedContainer(
    duration: const Duration(milliseconds: 200),
    height: 6,
    width: active ? 24 : 14,
    decoration: BoxDecoration(
      color: active ? AppColors.primary : AppColors.surfaceContainerHigh,
      borderRadius: BorderRadius.circular(999),
    ),
  );

  // ─── Media section ─────────────────────────────────────────────────────────

  Widget _buildImageSection() {
    return GestureDetector(
      onTap: _converting ? null : _showMediaOptions,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(24),
        child: AspectRatio(
          aspectRatio: 16 / 9,
          child: _selectedMedia != null
              ? Stack(
                  fit: StackFit.expand,
                  children: [
                    // Preview: video thumbnail placeholder or image
                    if (_isVideo)
                      Container(
                        color: Colors.black87,
                        child: Center(
                          child: _converting
                              ? const Column(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    CircularProgressIndicator(
                                      color: Colors.white,
                                      strokeWidth: 2.5,
                                    ),
                                    SizedBox(height: 12),
                                    Text(
                                      'Converting video...',
                                      style: TextStyle(
                                        color: Colors.white70,
                                        fontSize: 13,
                                        fontWeight: FontWeight.w500,
                                      ),
                                    ),
                                  ],
                                )
                              : const Icon(
                                  Icons.play_circle_fill,
                                  color: Colors.white,
                                  size: 56,
                                ),
                        ),
                      )
                    else
                      Image.file(_selectedMedia!, fit: BoxFit.cover),
                    // Bottom overlay
                    Positioned(
                      bottom: 0,
                      left: 0,
                      right: 0,
                      child: Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 12,
                        ),
                        decoration: BoxDecoration(
                          gradient: LinearGradient(
                            begin: Alignment.topCenter,
                            end: Alignment.bottomCenter,
                            colors: [
                              Colors.transparent,
                              Colors.black.withOpacity(0.45),
                            ],
                          ),
                        ),
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Container(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 12,
                                vertical: 6,
                              ),
                              decoration: BoxDecoration(
                                color: Colors.white.withOpacity(0.85),
                                borderRadius: BorderRadius.circular(999),
                              ),
                              child: Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  Icon(
                                    _isVideo
                                        ? Icons.videocam_outlined
                                        : Icons.photo_outlined,
                                    size: 12,
                                    color: AppColors.onSurface,
                                  ),
                                  const SizedBox(width: 4),
                                  Text(
                                    _converting ? 'Converting...' : (_isVideo ? 'Video added' : 'Tap to change'),
                                    style: TextStyle(
                                      fontSize: 11,
                                      fontWeight: FontWeight.w600,
                                      color: AppColors.onSurface,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            GestureDetector(
                              onTap: _converting ? null : () => setState(() {
                                _selectedMedia = null;
                                _isVideo = false;
                              }),
                              child: Container(
                                width: 34,
                                height: 34,
                                decoration: const BoxDecoration(
                                  color: Colors.white,
                                  shape: BoxShape.circle,
                                ),
                                child: Icon(
                                  Icons.delete_outline,
                                  color: AppColors.errorStrong,
                                  size: 18,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ],
                )
              : Container(
                  color: AppColors.surfaceContainer,
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Container(
                        width: 56,
                        height: 56,
                        decoration: BoxDecoration(
                          color: AppColors.cardSurface,
                          shape: BoxShape.circle,
                          boxShadow: [
                            BoxShadow(
                              color: AppColors.shadow,
                              blurRadius: 12,
                            ),
                          ],
                        ),
                        child: Icon(
                          Icons.perm_media_outlined,
                          color: AppColors.primary,
                          size: 26,
                        ),
                      ),
                      const SizedBox(height: 12),
                      Text(
                        'Add Photo or Video',
                        style: TextStyle(
                          fontFamily: 'Plus Jakarta Sans',
                          fontWeight: FontWeight.w700,
                          fontSize: 15,
                          color: AppColors.onSurface,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        'Photo, MP4 or MOV',
                        style: TextStyle(
                          fontSize: 12,
                          color: AppColors.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ),
                ),
        ),
      ),
    );
  }

  // ─── Location card ─────────────────────────────────────────────────────────

  Widget _buildLocationCard() {
    return Container(
      decoration: BoxDecoration(
        color: AppColors.surfaceContainer,
        borderRadius: BorderRadius.circular(20),
      ),
      clipBehavior: Clip.hardEdge,
      child: Column(
        children: [
          // Map with floating search bar overlay
          SizedBox(
            height: 220,
            width: double.infinity,
            child: Stack(
              children: [
                FlutterMap(
                  mapController: _mapController,
                  options: MapOptions(
                    initialCenter: _pinLocation,
                    initialZoom: 15,
                    onTap: (_, latLng) => _movePin(latLng),
                  ),
                  children: [
                    TileLayer(
                      urlTemplate:
                          'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
                      userAgentPackageName: 'com.bounswe2026group1.mapcess',
                    ),
                    MarkerLayer(
                      markers: [
                        Marker(
                          point: _pinLocation,
                          child: Icon(
                            Icons.location_on,
                            color: AppColors.primary,
                            size: 36,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
                // Floating search bar
                Positioned(
                  top: 10,
                  left: 10,
                  right: 10,
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      _buildLocationSearchBar(),
                      if (_locationSearchActive && _locationResults.isNotEmpty)
                        const SizedBox(height: 6),
                      if (_locationSearchActive && _locationResults.isNotEmpty)
                        _buildLocationResultsList(),
                    ],
                  ),
                ),
                // Expand button
                Positioned(
                  bottom: 10,
                  right: 10,
                  child: GestureDetector(
                    onTap: _openFullScreenPicker,
                    child: Container(
                      padding: const EdgeInsets.all(8),
                      decoration: BoxDecoration(
                        color: AppColors.cardSurface,
                        borderRadius: BorderRadius.circular(10),
                        boxShadow: [
                          BoxShadow(
                            color: AppColors.shadow,
                            blurRadius: 8,
                            offset: const Offset(0, 2),
                          ),
                        ],
                      ),
                      child: Icon(
                        Icons.fullscreen,
                        color: AppColors.primary,
                        size: 22,
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
          // Coordinate row
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            child: Row(
              children: [
                Icon(
                  Icons.near_me_outlined,
                  size: 16,
                  color: AppColors.secondary,
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'Search or tap the map to set location',
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w600,
                      color: AppColors.onSurface,
                    ),
                  ),
                ),
                Text(
                  '${_pinLocation.latitude.toStringAsFixed(4)}, '
                  '${_pinLocation.longitude.toStringAsFixed(4)}',
                  style: TextStyle(
                    fontSize: 10,
                    color: AppColors.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildLocationSearchBar() {
    return Material(
      elevation: 4,
      shadowColor: AppColors.shadow,
      borderRadius: BorderRadius.circular(12),
      color: AppColors.cardSurface,
      child: Row(
        children: [
          if (_locationSearchActive)
            IconButton(
              icon: Icon(Icons.arrow_back,
                  color: AppColors.primary, size: 20),
              onPressed: () {
                setState(() {
                  _locationSearchActive = false;
                  _locationResults = [];
                  _locationSearchController.clear();
                });
                _locationSearchFocus.unfocus();
              },
            )
          else
            Padding(
              padding: EdgeInsets.only(left: 12),
              child: Icon(Icons.search, color: AppColors.primary, size: 20),
            ),
          Expanded(
            child: TextField(
              controller: _locationSearchController,
              focusNode: _locationSearchFocus,
              onTap: () {
                if (!_locationSearchActive) {
                  setState(() => _locationSearchActive = true);
                }
              },
              onChanged: _onLocationSearchChanged,
              textInputAction: TextInputAction.search,
              onSubmitted: _searchLocation,
              style: TextStyle(fontSize: 13, color: AppColors.onSurface),
              decoration: InputDecoration(
                hintText: 'Search for a place…',
                hintStyle: TextStyle(
                  color: AppColors.onSurfaceVariant,
                  fontSize: 13,
                ),
                border: InputBorder.none,
                contentPadding: const EdgeInsets.symmetric(
                  horizontal: 8,
                  vertical: 12,
                ),
              ),
            ),
          ),
          if (_locationSearchLoading)
            Padding(
              padding: EdgeInsets.symmetric(horizontal: 10),
              child: SizedBox(
                width: 16,
                height: 16,
                child: CircularProgressIndicator(
                  strokeWidth: 2,
                  color: AppColors.primary,
                ),
              ),
            )
          else if (_locationSearchController.text.isNotEmpty)
            IconButton(
              icon: Icon(Icons.clear,
                  color: AppColors.onSurfaceVariant, size: 18),
              onPressed: () {
                _locationSearchController.clear();
                setState(() => _locationResults = []);
              },
            )
          else
            const SizedBox(width: 8),
        ],
      ),
    );
  }

  Widget _buildLocationResultsList() {
    return Material(
      elevation: 4,
      shadowColor: AppColors.shadow,
      borderRadius: BorderRadius.circular(12),
      color: AppColors.cardSurface,
      child: ListView.separated(
        padding: const EdgeInsets.symmetric(vertical: 4),
        shrinkWrap: true,
        physics: const NeverScrollableScrollPhysics(),
        itemCount: _locationResults.length,
        separatorBuilder: (_, __) => const Divider(height: 1, indent: 44),
        itemBuilder: (context, i) {
          final place = _locationResults[i];
          return ListTile(
            dense: true,
            leading: Icon(
              Icons.location_on_outlined,
              color: AppColors.primary,
              size: 18,
            ),
            title: Text(
              place.displayName.split(',').first,
              style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w600,
                color: AppColors.onSurface,
              ),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
            subtitle: Text(
              place.displayName,
              style: TextStyle(
                fontSize: 11,
                color: AppColors.onSurfaceVariant,
              ),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
            onTap: () => _selectLocation(place),
          );
        },
      ),
    );
  }

  // ─── Description ───────────────────────────────────────────────────────────

  Widget _buildDescriptionField() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'DESCRIBE THE ISSUE',
          style: TextStyle(
            fontSize: 10,
            fontWeight: FontWeight.w700,
            letterSpacing: 1.4,
            color: AppColors.secondary,
          ),
        ),
        const SizedBox(height: 8),
        Container(
          decoration: BoxDecoration(
            color: AppColors.surfaceContainerLowest,
            borderRadius: BorderRadius.circular(20),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withOpacity(0.04),
                blurRadius: 10,
              ),
            ],
          ),
          child: Stack(
            children: [
              TextField(
                controller: _descController,
                maxLines: 5,
                maxLength: 1000,
                style: TextStyle(
                  fontSize: 14,
                  color: AppColors.onSurface,
                  height: 1.5,
                ),
                decoration: InputDecoration(
                  hintText:
                      'What\'s happening? Be as specific as possible to help our crews find it…',
                  hintStyle: TextStyle(
                    color: AppColors.outlineVariant,
                    fontSize: 14,
                  ),
                  border: InputBorder.none,
                  contentPadding: const EdgeInsets.fromLTRB(18, 18, 18, 40),
                  counterText: '',
                ),
              ),
              Positioned(
                bottom: 12,
                right: 14,
                child: Text(
                  'Min 10 characters',
                  style: TextStyle(
                    fontSize: 10,
                    fontWeight: FontWeight.w600,
                    color: AppColors.outlineVariant,
                    letterSpacing: 0.3,
                  ),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  // ─── Category selector ─────────────────────────────────────────────────────

  Widget _buildCategorySelector() {
    final negative = ReportTag.values.where((t) => !t.isPositive).toList();
    final positive = ReportTag.values.where((t) => t.isPositive).toList();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'SELECT CATEGORY',
          style: TextStyle(
            fontSize: 10,
            fontWeight: FontWeight.w700,
            letterSpacing: 1.4,
            color: AppColors.secondary,
          ),
        ),
        const SizedBox(height: 10),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: negative.map((tag) => _buildTagChip(tag)).toList(),
        ),
        const SizedBox(height: 14),
        Row(
          children: [
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
              decoration: BoxDecoration(
                color: AppColors.primary.withOpacity(0.1),
                borderRadius: BorderRadius.circular(999),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(Icons.check_circle, size: 11, color: AppColors.primary),
                  SizedBox(width: 4),
                  Text(
                    'ACCESSIBILITY FEATURE',
                    style: TextStyle(
                      fontSize: 10,
                      fontWeight: FontWeight.w700,
                      letterSpacing: 1.2,
                      color: AppColors.primary,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
        const SizedBox(height: 8),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: positive.map((tag) => _buildTagChip(tag)).toList(),
        ),
      ],
    );
  }

  Widget _buildTagChip(ReportTag tag) {
    final selected = _selectedTag == tag;
    final unselectedBg = tag.isPositive
        ? AppColors.primary.withOpacity(0.1)
        : AppColors.infoContainer;
    final unselectedFg = tag.isPositive
        ? AppColors.primary
        : AppColors.onInfoContainer;
    return GestureDetector(
      onTap: () => setState(() => _selectedTag = tag),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 180),
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
        decoration: BoxDecoration(
          color: selected ? tag.color : unselectedBg,
          borderRadius: BorderRadius.circular(999),
          border: tag.isPositive && !selected
              ? Border.all(color: AppColors.primary.withOpacity(0.35))
              : null,
          boxShadow: selected
              ? [
                  BoxShadow(
                    color: tag.color.withOpacity(0.3),
                    blurRadius: 10,
                    offset: const Offset(0, 3),
                  ),
                ]
              : null,
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              tag.icon,
              size: 16,
              color: selected ? AppColors.onPrimarySolid : unselectedFg,
            ),
            const SizedBox(width: 6),
            Text(
              tag.label,
              style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w600,
                color: selected ? AppColors.onPrimarySolid : unselectedFg,
              ),
            ),
          ],
        ),
      ),
    );
  }

  // ─── Impact card ───────────────────────────────────────────────────────────

  Widget _buildImpactCard() {
    if (_selectedTag.isPositive) {
      return Container(
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          color: AppColors.primary.withOpacity(0.08),
          borderRadius: BorderRadius.circular(20),
          border: Border.all(color: AppColors.primary.withOpacity(0.25)),
        ),
        child: Row(
          children: [
            Container(
              width: 44,
              height: 44,
              decoration: BoxDecoration(
                color: AppColors.primary.withOpacity(0.15),
                shape: BoxShape.circle,
              ),
              child: Icon(Icons.accessible, color: AppColors.primary, size: 24),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'ACCESSIBILITY FEATURE',
                    style: TextStyle(
                      fontSize: 10,
                      fontWeight: FontWeight.w700,
                      letterSpacing: 1.4,
                      color: AppColors.primary,
                    ),
                  ),
                  SizedBox(height: 4),
                  Text(
                    'You\'re reporting something that helps people get around. Thank you!',
                    style: TextStyle(
                      fontSize: 11,
                      color: AppColors.primary,
                      height: 1.5,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      );
    }

    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainer,
        borderRadius: BorderRadius.circular(20),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'IMPACT LEVEL',
                style: TextStyle(
                  fontSize: 10,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 1.4,
                  color: AppColors.secondary,
                ),
              ),
              Text(
                _selectedTag == ReportTag.brokenElevator ||
                        _selectedTag == ReportTag.missingRamp
                    ? 'High'
                    : _selectedTag == ReportTag.construction ||
                            _selectedTag == ReportTag.wetFloor
                        ? 'Moderate'
                        : 'Low',
                style: TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w700,
                  color: AppColors.primary,
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          ClipRRect(
            borderRadius: BorderRadius.circular(999),
            child: Container(
              height: 8,
              color: AppColors.surfaceContainerHigh,
              child: LayoutBuilder(
                builder: (_, c) {
                  final frac = _selectedTag == ReportTag.brokenElevator ||
                          _selectedTag == ReportTag.missingRamp
                      ? 0.9
                      : _selectedTag == ReportTag.construction ||
                              _selectedTag == ReportTag.wetFloor
                          ? 0.55
                          : 0.25;
                  return Align(
                    alignment: Alignment.centerLeft,
                    child: Container(
                      width: c.maxWidth * frac,
                      height: 8,
                      decoration: BoxDecoration(
                        gradient: LinearGradient(
                          colors: [AppColors.primaryAccent, AppColors.primary],
                        ),
                      ),
                    ),
                  );
                },
              ),
            ),
          ),
          const SizedBox(height: 10),
          Text(
            'This helps us prioritize based on community safety and infrastructure health.',
            style: TextStyle(
              fontSize: 11,
              color: AppColors.onSurfaceVariant,
              height: 1.5,
            ),
          ),
        ],
      ),
    );
  }

  // ─── Bottom bar ───────────────────────────────────────────────────────────

  Widget _buildBottomBar() {
    return Container(
      padding: const EdgeInsets.fromLTRB(20, 16, 20, 36),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest.withOpacity(0.95),
        borderRadius: const BorderRadius.vertical(top: Radius.circular(32)),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.06),
            blurRadius: 30,
            offset: const Offset(0, -4),
          ),
        ],
      ),
      child: Row(
        children: [
          Expanded(
            child: GestureDetector(
              onTap: (_submitting || _converting) ? null : _submit,
              child: Container(
                height: 56,
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    colors: [AppColors.primary, AppColors.primaryDim],
                  ),
                  borderRadius: BorderRadius.circular(999),
                  boxShadow: [
                    BoxShadow(
                      color: AppColors.primary.withOpacity(0.3),
                      blurRadius: 20,
                      offset: const Offset(0, 8),
                    ),
                  ],
                ),
                child: _submitting
                    ? Center(
                        child: SizedBox(
                          width: 22,
                          height: 22,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: AppColors.onPrimary,
                          ),
                        ),
                      )
                    : Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Text(
                            'Post Report',
                            style: TextStyle(
                              fontFamily: 'Plus Jakarta Sans',
                              fontWeight: FontWeight.w700,
                              fontSize: 17,
                              color: AppColors.onPrimary,
                            ),
                          ),
                          SizedBox(width: 10),
                          Icon(Icons.send, color: AppColors.onPrimary, size: 20),
                        ],
                      ),
              ),
            ),
          ),
          const SizedBox(width: 12),
          Container(
            width: 56,
            height: 56,
            decoration: BoxDecoration(
              color: AppColors.surfaceContainerHigh,
              borderRadius: BorderRadius.circular(999),
            ),
            child: Icon(
              Icons.bookmark_border,
              color: AppColors.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
  }
}

// ─── Full-screen location picker ──────────────────────────────────────────────

class _LocationPickerScreen extends StatefulWidget {
  final LatLng initial;
  const _LocationPickerScreen({required this.initial});

  @override
  State<_LocationPickerScreen> createState() => _LocationPickerScreenState();
}

class _LocationPickerScreenState extends State<_LocationPickerScreen> {
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
      // ignore
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
                      color: AppColors.primary.withOpacity(0.35),
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
                    SizedBox(width: 10),
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
                color: AppColors.primary.withOpacity(0.1),
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

  factory _Place.fromJson(Map<String, dynamic> json) => _Place(
    displayName: json['display_name'] as String,
    lat: double.parse(json['lat'] as String),
    lon: double.parse(json['lon'] as String),
  );
}

