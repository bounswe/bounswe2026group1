import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';
import 'package:provider/provider.dart';

import '../models/report_model.dart';
import '../services/api_service.dart';
import '../services/auth_service.dart';
import '../theme/app_colors.dart';
import '../widgets/objects_section.dart';

/// Result returned from [EditReportScreen]. The detail screen below uses
/// it to either swap in the new model (save) or pop itself out of the way
/// (delete) — keeping both pops on a single, serial Navigator chain so no
/// pop animations overlap.
sealed class EditReportOutcome {
  const EditReportOutcome();
}

class EditReportUpdated extends EditReportOutcome {
  final ReportModel report;
  const EditReportUpdated(this.report);
}

class EditReportDeleted extends EditReportOutcome {
  const EditReportDeleted();
}

/// Author-only edit form. Pre-populates from [report] and saves via
/// PUT /api/reports/{id}. Pops with an [EditReportOutcome] describing what
/// happened — `null` means the user backed out without changes.
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
  late final MapController _mapController;

  late ReportEnvironment _environment;
  late LatLng _pin;
  late final List<ObjectDraft> _objects;

  bool _saving = false;
  bool _deleting = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    final r = widget.report;
    _descController = TextEditingController(text: r.description);
    _environment = r.environment;
    _pin = LatLng(r.latitude, r.longitude);
    _mapController = MapController();
    // Hydrate one draft per saved object — JSON measurements are decoded
    // back into the per-spec map by ObjectDraft.fromReportObject.
    _objects = [
      for (int i = 0; i < r.objects.length; i++)
        ObjectDraft.fromReportObject(r.objects[i], id: 'edit_obj_$i'),
    ];
  }

  @override
  void dispose() {
    _descController.dispose();
    _mapController.dispose();
    super.dispose();
  }

  bool get _isFeature => widget.report.reportType == ReportType.feature;

  /// Mirrors the make-report flow: when the env flips, drop the type+
  /// issues+measurements on cards whose ObjectType doesn't support the
  /// new environment so the form can't submit invalid combos.
  void _onEnvironmentChanged(ReportEnvironment next) {
    setState(() {
      _environment = next;
      for (final o in _objects) {
        final type = o.objectType;
        if (type != null && !type.supports(next)) {
          o.objectType = null;
          o.issues.clear();
          o.measurements.clear();
        }
      }
    });
  }

  Future<void> _save() async {
    final desc = _descController.text.trim();
    if (desc.length < 10) {
      setState(() => _error = 'Description must be at least 10 characters.');
      return;
    }
    if (_objects.isEmpty) {
      setState(
          () => _error = 'Add at least one object to describe the report.');
      return;
    }
    for (final o in _objects) {
      if (o.objectType == null) {
        setState(() => _error = 'Select a type for every object card.');
        return;
      }
      if (!_isFeature && o.issues.isEmpty) {
        setState(() => _error =
            'Pick at least one issue for the "${o.objectType!.label}" object.');
        return;
      }
    }

    setState(() {
      _saving = true;
      _error = null;
    });

    try {
      final auth = context.read<AuthService>();
      final reportObjects = _objects.map((o) {
        // Same JSON-encoded measurements shape the create form writes — keeps
        // the backend's free-text field consistent across clients.
        final filled = <String, num>{};
        for (final e in o.measurements.entries) {
          final v = num.tryParse(e.value.trim());
          if (v != null) filled[e.key] = v;
        }
        return ReportObject(
          objectType: o.objectType!,
          issues: o.issues.toList(),
          measurements: filled.isEmpty ? null : jsonEncode(filled),
        );
      }).toList();

      final updated = await auth.api.updateReport(
        reportId: widget.report.reportId,
        description: desc,
        environment: _environment,
        latitude: _pin.latitude,
        longitude: _pin.longitude,
        objects: reportObjects,
      );
      if (!mounted) return;
      Navigator.pop(context, EditReportUpdated(updated));
    } on ApiException catch (e) {
      if (mounted) {
        setState(() {
          _saving = false;
          _error = e.userMessage;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _saving = false;
          _error = 'Could not save changes. Try again.';
        });
      }
    }
  }

  Future<void> _confirmAndDelete() async {
    if (_deleting || _saving) return;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.surfaceContainerLowest,
        shape:
            RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        icon: Icon(
          Icons.delete_forever_outlined,
          color: AppColors.error,
          size: 32,
        ),
        title: Text(
          'Delete this report?',
          style: TextStyle(
            fontFamily: 'Plus Jakarta Sans',
            fontWeight: FontWeight.w800,
            fontSize: 18,
            color: AppColors.onSurface,
          ),
        ),
        content: Text(
          'This permanently removes the report along with its objects, '
          'media, and comments. This action can\'t be undone.',
          style: TextStyle(
            fontSize: 13,
            color: AppColors.onSurfaceVariant,
            height: 1.45,
          ),
        ),
        actionsPadding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: Text(
              'Cancel',
              style: TextStyle(
                color: AppColors.onSurfaceVariant,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, true),
            style: FilledButton.styleFrom(
              backgroundColor: AppColors.error,
              foregroundColor: Colors.white,
            ),
            child: const Text('Delete'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;

    setState(() {
      _deleting = true;
      _error = null;
    });

    try {
      await context.read<AuthService>().api.deleteReport(widget.report.reportId);
      if (!mounted) return;
      // Pop only this screen — the detail screen below reads the
      // EditReportDeleted result and pops itself, keeping the two pops
      // serial instead of stacking two simultaneous exit animations.
      Navigator.pop(context, const EditReportDeleted());
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _deleting = false;
        _error = 'Could not delete — ${e.userMessage}';
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _deleting = false;
        _error = 'Could not delete report. Try again.';
      });
    }
  }

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
                  ObjectsSection(
                    objects: _objects,
                    reportType: widget.report.reportType,
                    environment: _environment,
                    onChanged: () => setState(() {}),
                  ),
                  if (_error != null) ...[
                    const SizedBox(height: 16),
                    _buildErrorBanner(),
                  ],
                  const SizedBox(height: 28),
                  _buildDeleteButton(),
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
                      child: Icon(Icons.location_on,
                          color: AppColors.primary, size: 36),
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
    const items = [
      _EnvOption(
        env: ReportEnvironment.outdoor,
        icon: Icons.wb_sunny_outlined,
        label: 'Outdoor',
      ),
      _EnvOption(
        env: ReportEnvironment.indoor,
        icon: Icons.home_outlined,
        label: 'Indoor',
      ),
    ];
    return _section(
      label: 'ENVIRONMENT',
      child: Row(
        children: [
          for (int i = 0; i < items.length; i++) ...[
            if (i > 0) const SizedBox(width: 8),
            Expanded(
              child: _envButton(
                option: items[i],
                selected: _environment == items[i].env,
                onTap: () => _onEnvironmentChanged(items[i].env),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _envButton({
    required _EnvOption option,
    required bool selected,
    required VoidCallback onTap,
  }) {
    final fg = selected ? AppColors.primary : AppColors.onSurfaceVariant;
    final borderColor =
        selected ? AppColors.primary : AppColors.outlineVariant;
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 180),
        padding: const EdgeInsets.symmetric(vertical: 12),
        decoration: BoxDecoration(
          color: selected
              ? AppColors.primary.withValues(alpha: 0.07)
              : AppColors.surfaceContainerLowest,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: borderColor, width: 2),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(option.icon, size: 16, color: fg),
            const SizedBox(width: 8),
            Text(
              option.label,
              style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w700,
                color: fg,
              ),
            ),
          ],
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

  Widget _buildDeleteButton() {
    final disabled = _saving || _deleting;
    return Opacity(
      opacity: disabled && _saving ? 0.6 : 1,
      child: GestureDetector(
        onTap: disabled ? null : _confirmAndDelete,
        child: Container(
          width: double.infinity,
          padding: const EdgeInsets.symmetric(vertical: 14),
          decoration: BoxDecoration(
            color: AppColors.errorContainer,
            borderRadius: BorderRadius.circular(14),
            border: Border.all(color: AppColors.errorContainerBorder),
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              if (_deleting)
                SizedBox(
                  width: 18,
                  height: 18,
                  child: CircularProgressIndicator(
                    strokeWidth: 2,
                    color: AppColors.error,
                  ),
                )
              else
                Icon(Icons.delete_outline,
                    color: AppColors.error, size: 20),
              const SizedBox(width: 10),
              Text(
                _deleting ? 'Deleting…' : 'Delete Report',
                style: TextStyle(
                  fontFamily: 'Plus Jakarta Sans',
                  fontWeight: FontWeight.w700,
                  fontSize: 14,
                  color: AppColors.error,
                ),
              ),
            ],
          ),
        ),
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
}

class _EnvOption {
  final ReportEnvironment env;
  final IconData icon;
  final String label;
  const _EnvOption({
    required this.env,
    required this.icon,
    required this.label,
  });
}
