import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';
import 'package:provider/provider.dart';

import '../models/report_model.dart';
import '../services/api_service.dart';
import '../services/auth_service.dart';
import '../theme/app_colors.dart';

/// Author-only edit form. Pre-populates from [report] and saves via
/// PUT /api/reports/{id}. Pops with the updated [ReportModel] on success.
///
/// Note: `mediaIdsToRemove` is not surfaced because the backend's report
/// response currently exposes media URLs without IDs. Once IDs land in the
/// response, a media removal section can be added here.
class EditReportScreen extends StatefulWidget {
  final ReportModel report;

  const EditReportScreen({super.key, required this.report});

  @override
  State<EditReportScreen> createState() => _EditReportScreenState();
}

class _EditReportScreenState extends State<EditReportScreen> {
  late final TextEditingController _descController;
  late final TextEditingController _measurementsController;
  late final MapController _mapController;

  late ReportEnvironment _environment;
  late ObjectType _objectType;
  late LatLng _pin;
  late Set<IssueType> _selectedIssues;

  bool _saving = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    final r = widget.report;
    _descController = TextEditingController(text: r.description);
    _environment = r.environment;
    _pin = LatLng(r.latitude, r.longitude);
    _mapController = MapController();

    final primary = r.primaryObject;
    _objectType = primary?.objectType ?? ObjectType.ramp;
    _selectedIssues = primary != null ? {...primary.issues} : <IssueType>{};
    _measurementsController =
        TextEditingController(text: primary?.measurements ?? '');
  }

  @override
  void dispose() {
    _descController.dispose();
    _measurementsController.dispose();
    _mapController.dispose();
    super.dispose();
  }

  // ── Submit ──────────────────────────────────────────────────────────────

  bool get _isFeature => widget.report.reportType == ReportType.feature;

  Future<void> _save() async {
    final desc = _descController.text.trim();
    if (desc.length < 10) {
      setState(() => _error = 'Description must be at least 10 characters.');
      return;
    }
    if (!_isFeature && _selectedIssues.isEmpty) {
      setState(() => _error = 'Pick at least one issue for this object.');
      return;
    }

    setState(() {
      _saving = true;
      _error = null;
    });

    try {
      final auth = context.read<AuthService>();
      final measurements = _measurementsController.text.trim();
      final updatedObject = ReportObject(
        objectType: _objectType,
        issues: _selectedIssues.toList(),
        measurements: measurements.isEmpty ? null : measurements,
      );
      final updated = await auth.api.updateReport(
        reportId: widget.report.reportId,
        description: desc,
        environment: _environment,
        latitude: _pin.latitude,
        longitude: _pin.longitude,
        objects: [updatedObject],
      );
      if (!mounted) return;
      Navigator.pop(context, updated);
    } on ApiException catch (e) {
      if (mounted) {
        setState(() {
          _saving = false;
          _error = e.userMessage;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _saving = false;
          _error = 'Could not save changes. Try again.';
        });
      }
    }
  }

  // ── Build ───────────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.surface,
      body: Column(
        children: [
          _buildTopBar(),
          Expanded(
            child: SingleChildScrollView(
              padding: const EdgeInsets.fromLTRB(20, 12, 20, 24),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _buildLocationCard(),
                  const SizedBox(height: 20),
                  _buildDescriptionField(),
                  const SizedBox(height: 20),
                  _buildEnvironmentSelector(),
                  const SizedBox(height: 20),
                  _buildObjectSelector(),
                  if (!_isFeature) ...[
                    const SizedBox(height: 20),
                    _buildIssueSelector(),
                  ],
                  const SizedBox(height: 20),
                  _buildMeasurementsField(),
                  if (_error != null) ...[
                    const SizedBox(height: 16),
                    _buildErrorBanner(),
                  ],
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

  Widget _buildTopBar() {
    return SafeArea(
      bottom: false,
      child: Container(
        color: AppColors.surface.withValues(alpha: 0.92),
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
        child: Row(
          children: [
            IconButton(
              icon: Icon(Icons.close, color: AppColors.primary),
              onPressed: _saving ? null : () => Navigator.pop(context),
            ),
            const SizedBox(width: 4),
            Text(
              'Edit Report',
              style: TextStyle(
                fontFamily: 'Plus Jakarta Sans',
                fontWeight: FontWeight.w800,
                fontSize: 22,
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
                '#${widget.report.reportId}',
                style: TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.w700,
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

  Widget _buildLocationCard() {
    return Container(
      decoration: BoxDecoration(
        color: AppColors.surfaceContainer,
        borderRadius: BorderRadius.circular(20),
      ),
      clipBehavior: Clip.hardEdge,
      child: Column(
        children: [
          SizedBox(
            height: 200,
            width: double.infinity,
            child: FlutterMap(
              mapController: _mapController,
              options: MapOptions(
                initialCenter: _pin,
                initialZoom: 16,
                onTap: (_, latLng) => setState(() => _pin = latLng),
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
                      point: _pin,
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
          ),
          Padding(
            padding: const EdgeInsets.all(14),
            child: Row(
              children: [
                Icon(Icons.place_outlined,
                    size: 16, color: AppColors.onSurfaceVariant),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    '${_pin.latitude.toStringAsFixed(5)}, ${_pin.longitude.toStringAsFixed(5)}',
                    style: TextStyle(
                      fontSize: 12,
                      color: AppColors.onSurfaceVariant,
                    ),
                  ),
                ),
                Text(
                  'Tap map to move',
                  style: TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.w600,
                    color: AppColors.primary,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildDescriptionField() {
    return _section(
      label: 'DESCRIPTION',
      child: Container(
        decoration: BoxDecoration(
          color: AppColors.surfaceContainerLowest,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: AppColors.surfaceContainerHigh),
        ),
        child: TextField(
          controller: _descController,
          maxLines: 4,
          style: TextStyle(fontSize: 14, color: AppColors.onSurface),
          decoration: InputDecoration(
            hintText: 'Describe the issue clearly…',
            hintStyle: TextStyle(color: AppColors.outlineVariant, fontSize: 13),
            border: InputBorder.none,
            contentPadding:
                const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
          ),
        ),
      ),
    );
  }

  Widget _buildEnvironmentSelector() {
    return _section(
      label: 'ENVIRONMENT',
      child: Row(
        children: ReportEnvironment.values
            .map((e) => Expanded(
                  child: Padding(
                    padding: EdgeInsets.only(
                        right: e == ReportEnvironment.values.last ? 0 : 8),
                    child: _segmentChip(
                      icon: e.icon,
                      label: e.label,
                      selected: _environment == e,
                      onTap: () => setState(() => _environment = e),
                    ),
                  ),
                ))
            .toList(),
      ),
    );
  }

  Widget _buildObjectSelector() {
    return _section(
      label: 'OBJECT',
      child: Wrap(
        spacing: 8,
        runSpacing: 8,
        children: ObjectType.values.map((type) {
          final selected = _objectType == type;
          return GestureDetector(
            onTap: () => setState(() {
              _objectType = type;
              _selectedIssues.removeWhere((i) => !i.isValidFor(type));
            }),
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 180),
              padding:
                  const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
              decoration: BoxDecoration(
                color: selected ? type.color : AppColors.surfaceContainer,
                borderRadius: BorderRadius.circular(999),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(
                    type.icon,
                    size: 16,
                    color: selected
                        ? AppColors.onPrimarySolid
                        : AppColors.onSurfaceVariant,
                  ),
                  const SizedBox(width: 6),
                  Text(
                    type.label,
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w600,
                      color: selected
                          ? AppColors.onPrimarySolid
                          : AppColors.onSurface,
                    ),
                  ),
                ],
              ),
            ),
          );
        }).toList(),
      ),
    );
  }

  Widget _buildIssueSelector() {
    final issues = IssueType.issuesFor(_objectType);
    return _section(
      label: 'ISSUES (${_selectedIssues.length})',
      hint: 'Pick all that apply',
      child: Wrap(
        spacing: 8,
        runSpacing: 8,
        children: issues.map((issue) {
          final selected = _selectedIssues.contains(issue);
          return GestureDetector(
            onTap: () => setState(() {
              if (selected) {
                _selectedIssues.remove(issue);
              } else {
                _selectedIssues.add(issue);
              }
            }),
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 150),
              padding:
                  const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              decoration: BoxDecoration(
                color:
                    selected ? AppColors.primary : AppColors.surfaceContainer,
                borderRadius: BorderRadius.circular(999),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(
                    selected ? Icons.check : Icons.add,
                    size: 14,
                    color: selected
                        ? AppColors.onPrimarySolid
                        : AppColors.onSurfaceVariant,
                  ),
                  const SizedBox(width: 6),
                  Text(
                    issue.label,
                    style: TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                      color: selected
                          ? AppColors.onPrimarySolid
                          : AppColors.onSurface,
                    ),
                  ),
                ],
              ),
            ),
          );
        }).toList(),
      ),
    );
  }

  Widget _buildMeasurementsField() {
    return _section(
      label: 'MEASUREMENTS',
      hint: 'Optional — e.g. width 80cm, slope 12%',
      child: Container(
        decoration: BoxDecoration(
          color: AppColors.surfaceContainerLowest,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: AppColors.surfaceContainerHigh),
        ),
        child: TextField(
          controller: _measurementsController,
          maxLines: 2,
          style: TextStyle(fontSize: 14, color: AppColors.onSurface),
          decoration: InputDecoration(
            hintText: 'Add dimensions, slope, clearance…',
            hintStyle: TextStyle(color: AppColors.outlineVariant, fontSize: 13),
            border: InputBorder.none,
            contentPadding:
                const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
          ),
        ),
      ),
    );
  }

  Widget _buildErrorBanner() {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppColors.errorContainer,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppColors.errorContainerBorder),
      ),
      child: Row(
        children: [
          Icon(Icons.error_outline,
              color: AppColors.onErrorContainer, size: 18),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              _error!,
              style: TextStyle(
                fontSize: 13,
                color: AppColors.onErrorContainer,
                fontWeight: FontWeight.w500,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildBottomBar() {
    return Container(
      padding: const EdgeInsets.fromLTRB(20, 16, 20, 36),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest.withValues(alpha: 0.95),
        borderRadius: const BorderRadius.vertical(top: Radius.circular(32)),
        boxShadow: [
          BoxShadow(
            color: AppColors.shadow,
            blurRadius: 30,
            offset: const Offset(0, -4),
          ),
        ],
      ),
      child: GestureDetector(
        onTap: _saving ? null : _save,
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
                color: AppColors.primary.withValues(alpha: 0.3),
                blurRadius: 20,
                offset: const Offset(0, 8),
              ),
            ],
          ),
          child: _saving
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
                      'Save Changes',
                      style: TextStyle(
                        fontFamily: 'Plus Jakarta Sans',
                        fontWeight: FontWeight.w700,
                        fontSize: 16,
                        color: AppColors.onPrimary,
                      ),
                    ),
                    const SizedBox(width: 10),
                    Icon(Icons.check_rounded,
                        color: AppColors.onPrimary, size: 20),
                  ],
                ),
        ),
      ),
    );
  }

  // ── Shared chips / sections ─────────────────────────────────────────────

  Widget _section({
    required String label,
    required Widget child,
    String? hint,
  }) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Text(
              label,
              style: TextStyle(
                fontSize: 10,
                fontWeight: FontWeight.w700,
                letterSpacing: 1.4,
                color: AppColors.secondary,
              ),
            ),
            if (hint != null) ...[
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  hint,
                  style: TextStyle(
                    fontSize: 11,
                    color: AppColors.outlineVariant,
                  ),
                ),
              ),
            ],
          ],
        ),
        const SizedBox(height: 10),
        child,
      ],
    );
  }

  Widget _segmentChip({
    required IconData icon,
    required String label,
    required bool selected,
    required VoidCallback onTap,
  }) {
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 180),
        padding: const EdgeInsets.symmetric(vertical: 12),
        decoration: BoxDecoration(
          color: selected ? AppColors.primary : AppColors.surfaceContainer,
          borderRadius: BorderRadius.circular(14),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              icon,
              size: 18,
              color: selected
                  ? AppColors.onPrimarySolid
                  : AppColors.onSurfaceVariant,
            ),
            const SizedBox(width: 8),
            Text(
              label,
              style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w600,
                color:
                    selected ? AppColors.onPrimarySolid : AppColors.onSurface,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
