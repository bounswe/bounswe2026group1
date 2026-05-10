import 'dart:convert';

import 'package:flutter/material.dart';

import '../models/report_model.dart';
import '../theme/app_colors.dart';

/// Mutable per-card state for one object on a draft (create or edit) report.
/// Lives outside the screen state so multiple screens can share the same
/// rendering widget — see [ObjectsSection].
class ObjectDraft {
  final String id;
  ObjectType? objectType;
  final Set<IssueType> issues;
  final Map<String, String> measurements;
  bool expanded;
  bool showMeasurements;

  ObjectDraft({
    required this.id,
    this.objectType,
    Set<IssueType>? issues,
    Map<String, String>? measurements,
    this.expanded = true,
    this.showMeasurements = false,
  })  : issues = issues ?? <IssueType>{},
        measurements = measurements ?? <String, String>{};

  /// Builds a draft from a saved [ReportObject] — used by the edit screen.
  /// Parses the JSON-encoded measurements blob the create form writes; if the
  /// blob isn't valid JSON we leave the map empty (the user can re-enter
  /// values or fall back to free text via the make-report form).
  factory ObjectDraft.fromReportObject(ReportObject obj, {required String id}) {
    final measurements = _decodeMeasurements(obj.measurements);
    return ObjectDraft(
      id: id,
      objectType: obj.objectType,
      issues: {...obj.issues},
      measurements: measurements,
      expanded: true,
      showMeasurements: measurements.isNotEmpty,
    );
  }
}

Map<String, String> _decodeMeasurements(String? raw) {
  if (raw == null) return {};
  final trimmed = raw.trim();
  if (!trimmed.startsWith('{')) return {};
  try {
    final decoded = jsonDecode(trimmed);
    if (decoded is! Map) return {};
    return decoded.map((k, v) => MapEntry(k.toString(), v.toString()));
  } catch (_) {
    return {};
  }
}

/// Shared "Objects" section used by the create and edit report screens.
/// Owns the rendering for numbered cards, the type picker, issue pills, and
/// the measurements block with accessibility hints.
///
/// State is mutated in place on the [ObjectDraft] instances; the parent
/// passes [onChanged] so the screen can re-`setState` to repaint.
class ObjectsSection extends StatelessWidget {
  final List<ObjectDraft> objects;
  final ReportType reportType;
  final ReportEnvironment environment;
  final VoidCallback onChanged;

  const ObjectsSection({
    super.key,
    required this.objects,
    required this.reportType,
    required this.environment,
    required this.onChanged,
  });

  // ── Mutators ───────────────────────────────────────────────────────────────

  /// Object types selectable given [reportType] AND [environment]:
  ///   • FEATURE locks selection to RAMP only.
  ///   • Each ObjectType declares which environments it supports
  ///     (`SIDEWALK`/`PEDESTRIAN_CROSSING`/`CURB_RAMP` outdoor only,
  ///     `WASHROOM`/`ROOM_SIGN` indoor only) so a flip of the env
  ///     selector hides types that no longer apply.
  List<ObjectType> get _visibleObjectTypes {
    final pool = reportType == ReportType.feature
        ? const [ObjectType.ramp]
        : ObjectType.values;
    return pool.where((t) => t.supports(environment)).toList();
  }

  /// Set of types already assigned to other cards — prevents picking the same
  /// type twice on the same report.
  Set<ObjectType> get _claimedTypes =>
      objects.map((o) => o.objectType).whereType<ObjectType>().toSet();

  void _addObject() {
    objects.add(
      ObjectDraft(id: 'obj_${DateTime.now().microsecondsSinceEpoch}'),
    );
    onChanged();
  }

  void _removeObject(String id) {
    objects.removeWhere((o) => o.id == id);
    onChanged();
  }

  void _toggleExpanded(ObjectDraft o) {
    o.expanded = !o.expanded;
    onChanged();
  }

  void _selectObjectType(ObjectDraft o, ObjectType type) {
    o.objectType = type;
    o.issues.clear();
    o.measurements.clear();
    onChanged();
  }

  void _toggleIssue(ObjectDraft o, IssueType issue) {
    if (!o.issues.add(issue)) o.issues.remove(issue);
    onChanged();
  }

  void _setMeasurement(ObjectDraft o, String key, String value) {
    if (value.isEmpty) {
      o.measurements.remove(key);
    } else {
      o.measurements[key] = value;
    }
    onChanged();
  }

  void _toggleShowMeasurements(ObjectDraft o) {
    o.showMeasurements = !o.showMeasurements;
    onChanged();
  }

  // ── Build ──────────────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Text(
              'OBJECTS',
              style: TextStyle(
                fontSize: 10,
                fontWeight: FontWeight.w700,
                letterSpacing: 1.4,
                color: AppColors.secondary,
              ),
            ),
            const SizedBox(width: 8),
            Text(
              'required',
              style: TextStyle(
                fontSize: 10,
                fontWeight: FontWeight.w600,
                color: AppColors.error,
              ),
            ),
          ],
        ),
        const SizedBox(height: 10),
        for (int i = 0; i < objects.length; i++) ...[
          if (i > 0) const SizedBox(height: 8),
          _buildObjectCard(objects[i], i + 1),
        ],
        if (objects.isNotEmpty) const SizedBox(height: 8),
        _buildAddObjectButton(),
      ],
    );
  }

  Widget _buildObjectCard(ObjectDraft draft, int index) {
    final type = draft.objectType;
    final hasType = type != null;
    return Container(
      decoration: BoxDecoration(
        color: AppColors.surfaceContainer,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: hasType
              ? AppColors.primary.withValues(alpha: 0.25)
              : AppColors.outlineVariant.withValues(alpha: 0.5),
          width: 2,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          InkWell(
            onTap: () => _toggleExpanded(draft),
            borderRadius: BorderRadius.circular(14),
            child: Padding(
              padding: const EdgeInsets.fromLTRB(14, 12, 8, 12),
              child: Row(
                children: [
                  Container(
                    width: 22,
                    height: 22,
                    decoration: BoxDecoration(
                      color: AppColors.primary,
                      shape: BoxShape.circle,
                    ),
                    alignment: Alignment.center,
                    child: Text(
                      '$index',
                      style: TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.w700,
                        color: AppColors.onPrimarySolid,
                      ),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      type?.label ?? 'Select a type…',
                      style: TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.w700,
                        fontStyle:
                            hasType ? FontStyle.normal : FontStyle.italic,
                        color: hasType
                            ? AppColors.onSurface
                            : AppColors.onSurfaceVariant,
                      ),
                    ),
                  ),
                  AnimatedRotation(
                    turns: draft.expanded ? 0.5 : 0,
                    duration: const Duration(milliseconds: 180),
                    child: Icon(Icons.expand_more,
                        color: AppColors.onSurfaceVariant, size: 20),
                  ),
                  IconButton(
                    iconSize: 18,
                    splashRadius: 20,
                    icon: Icon(Icons.delete_outline,
                        color: AppColors.onSurfaceVariant),
                    onPressed: () => _removeObject(draft.id),
                  ),
                ],
              ),
            ),
          ),
          if (draft.expanded) ...[
            Container(
              height: 1,
              color: AppColors.outlineVariant.withValues(alpha: 0.3),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(14, 12, 14, 14),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _buildObjectTypePicker(draft),
                  if (hasType && reportType == ReportType.obstacle) ...[
                    const SizedBox(height: 14),
                    _buildIssueChecklist(draft),
                  ],
                  if (hasType && measurementSpecsFor(type).isNotEmpty) ...[
                    const SizedBox(height: 14),
                    _buildMeasurementsBlock(draft),
                  ],
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildObjectTypePicker(ObjectDraft draft) {
    final visible = _visibleObjectTypes;
    final claimed = _claimedTypes;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'OBJECT TYPE',
          style: TextStyle(
            fontSize: 10,
            fontWeight: FontWeight.w700,
            letterSpacing: 1.4,
            color: AppColors.onSurfaceVariant,
          ),
        ),
        const SizedBox(height: 8),
        Wrap(
          spacing: 6,
          runSpacing: 6,
          children: visible.map((type) {
            final isSelected = draft.objectType == type;
            final isDisabled = !isSelected && claimed.contains(type);
            return _objectTypeCell(
              type: type,
              selected: isSelected,
              disabled: isDisabled,
              onTap:
                  isDisabled ? null : () => _selectObjectType(draft, type),
            );
          }).toList(),
        ),
      ],
    );
  }

  Widget _objectTypeCell({
    required ObjectType type,
    required bool selected,
    required bool disabled,
    required VoidCallback? onTap,
  }) {
    final fg = selected ? AppColors.primary : AppColors.onSurfaceVariant;
    final borderColor =
        selected ? AppColors.primary : AppColors.outlineVariant;
    return Opacity(
      opacity: disabled ? 0.35 : 1,
      child: GestureDetector(
        onTap: onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 150),
          width: 64,
          padding: const EdgeInsets.symmetric(vertical: 10),
          decoration: BoxDecoration(
            color: selected
                ? AppColors.primary.withValues(alpha: 0.08)
                : AppColors.surfaceContainerLowest,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: borderColor, width: 2),
          ),
          child: Column(
            children: [
              Icon(type.icon, size: 22, color: fg),
              const SizedBox(height: 4),
              Text(
                type.label,
                style: TextStyle(
                  fontSize: 10,
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

  Widget _buildIssueChecklist(ObjectDraft draft) {
    final type = draft.objectType!;
    final issues = IssueType.issuesFor(type);
    final selectedCount = draft.issues.length;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Text(
              'ISSUES',
              style: TextStyle(
                fontSize: 10,
                fontWeight: FontWeight.w700,
                letterSpacing: 1.4,
                color: AppColors.onSurfaceVariant,
              ),
            ),
            const SizedBox(width: 4),
            Text(
              '*',
              style: TextStyle(
                fontSize: 10,
                fontWeight: FontWeight.w700,
                color: AppColors.error,
              ),
            ),
            const Spacer(),
            if (selectedCount > 0)
              Text(
                '$selectedCount selected',
                style: TextStyle(
                  fontSize: 10,
                  fontWeight: FontWeight.w700,
                  color: AppColors.primary,
                ),
              ),
          ],
        ),
        const SizedBox(height: 8),
        Wrap(
          spacing: 6,
          runSpacing: 6,
          children:
              issues.map((issue) => _issuePill(draft, issue)).toList(),
        ),
      ],
    );
  }

  Widget _issuePill(ObjectDraft draft, IssueType issue) {
    final selected = draft.issues.contains(issue);
    return GestureDetector(
      onTap: () => _toggleIssue(draft, issue),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 150),
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        decoration: BoxDecoration(
          color:
              selected ? AppColors.primary : AppColors.surfaceContainerLowest,
          borderRadius: BorderRadius.circular(999),
          border: Border.all(
            color: selected
                ? AppColors.primary
                : AppColors.outlineVariant.withValues(alpha: 0.5),
            width: 1.5,
          ),
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
  }

  Widget _buildMeasurementsBlock(ObjectDraft draft) {
    final specs = measurementSpecsFor(draft.objectType!);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        InkWell(
          onTap: () => _toggleShowMeasurements(draft),
          borderRadius: BorderRadius.circular(6),
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 4),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(Icons.straighten, size: 16, color: AppColors.primary),
                const SizedBox(width: 6),
                Text(
                  draft.showMeasurements
                      ? 'Hide measurements'
                      : 'Show measurements (optional)',
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
        if (draft.showMeasurements) ...[
          const SizedBox(height: 8),
          for (final spec in specs) ...[
            _buildMeasurementField(draft, spec),
            const SizedBox(height: 10),
          ],
        ],
      ],
    );
  }

  Widget _buildMeasurementField(ObjectDraft draft, MeasurementSpec spec) {
    final value = draft.measurements[spec.key] ?? '';
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          spec.label,
          style: TextStyle(
            fontSize: 10,
            fontWeight: FontWeight.w700,
            color: AppColors.onSurfaceVariant,
          ),
        ),
        const SizedBox(height: 4),
        Container(
          decoration: BoxDecoration(
            color: AppColors.surfaceContainerLowest,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(
              color: AppColors.outlineVariant.withValues(alpha: 0.5),
            ),
          ),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              Expanded(
                child: TextFormField(
                  // Keying by id+key keeps focus and the visible text correct
                  // when the parent's `setState` rebuilds the section after a
                  // mutation elsewhere on the form.
                  key: ValueKey('${draft.id}_${spec.key}'),
                  initialValue: value,
                  keyboardType: const TextInputType.numberWithOptions(
                      decimal: true),
                  textAlignVertical: TextAlignVertical.center,
                  style: TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.w600,
                    color: AppColors.onSurface,
                  ),
                  decoration: InputDecoration(
                    isDense: true,
                    hintText: '—',
                    hintStyle: TextStyle(
                      color: AppColors.outlineVariant,
                      fontWeight: FontWeight.w400,
                    ),
                    border: InputBorder.none,
                    contentPadding: const EdgeInsets.symmetric(
                        horizontal: 14, vertical: 14),
                  ),
                  onChanged: (v) => _setMeasurement(draft, spec.key, v),
                ),
              ),
              Padding(
                padding: const EdgeInsets.fromLTRB(4, 6, 6, 6),
                child: Container(
                  padding: const EdgeInsets.symmetric(
                      horizontal: 10, vertical: 6),
                  decoration: BoxDecoration(
                    color: AppColors.surfaceContainer,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Text(
                    spec.unit,
                    style: TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.w700,
                      letterSpacing: 0.4,
                      color: AppColors.onSurfaceVariant,
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
        ..._buildVerdictLines(value, spec),
      ],
    );
  }

  /// Live accessibility feedback for the entered value:
  ///   • empty/unparseable → neutral hint showing the threshold
  ///   • value within limits → green ✓ "Accessible · …"
  ///   • value outside limits → red ⚠ banner explaining the violation
  /// Specs without any threshold render no hint at all.
  List<Widget> _buildVerdictLines(String rawValue, MeasurementSpec spec) {
    if (spec.accessibleMin == null && spec.accessibleMax == null) {
      return const [];
    }
    final threshold = _thresholdText(spec);
    final parsed = double.tryParse(rawValue.trim());
    if (parsed == null) {
      // Empty / non-numeric — show the rule so users see what to aim for.
      return [_hintRow(text: threshold, tone: AppColors.primary, icon: Icons.info_outline)];
    }
    final ok = spec.isAccessible(parsed);
    if (ok == true) {
      return [
        _hintRow(
          text: 'Accessible · $threshold',
          tone: AppColors.success,
          icon: Icons.check_circle,
        ),
      ];
    }
    return [
      _violationBanner(spec, parsed, threshold),
    ];
  }

  Widget _hintRow({
    required String text,
    required Color tone,
    required IconData icon,
  }) {
    return Padding(
      padding: const EdgeInsets.only(top: 4),
      child: Row(
        children: [
          Icon(icon, size: 12, color: tone),
          const SizedBox(width: 6),
          Expanded(
            child: Text(
              text,
              style: TextStyle(
                fontSize: 11,
                fontWeight: FontWeight.w600,
                color: tone,
              ),
            ),
          ),
        ],
      ),
    );
  }

  /// Slightly heavier treatment for out-of-range values — a red-tinted card
  /// so users notice the warning without it blending into the form.
  Widget _violationBanner(
      MeasurementSpec spec, double value, String threshold) {
    final unit = spec.unit;
    final tooLow =
        spec.accessibleMin != null && value < spec.accessibleMin!;
    final tooHigh =
        spec.accessibleMax != null && value > spec.accessibleMax!;
    final detail = tooLow
        ? 'Below the accessible minimum.'
        : tooHigh
            ? 'Above the accessible maximum.'
            : 'Outside the accessible range.';
    return Container(
      margin: const EdgeInsets.only(top: 6),
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
      decoration: BoxDecoration(
        color: AppColors.errorContainer,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: AppColors.errorContainerBorder),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(Icons.warning_amber_rounded,
              size: 14, color: AppColors.onErrorContainer),
          const SizedBox(width: 8),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '$detail Needs $threshold.',
                  style: TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.w700,
                    color: AppColors.onErrorContainer,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  'Reported value: ${_fmt(value)} $unit',
                  style: TextStyle(
                    fontSize: 11,
                    color: AppColors.onErrorContainer,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  String _thresholdText(MeasurementSpec s) {
    final unit = s.unit;
    if (s.accessibleMin != null && s.accessibleMax != null) {
      return '${_fmt(s.accessibleMin!)}–${_fmt(s.accessibleMax!)} $unit';
    }
    if (s.accessibleMin != null) return '≥ ${_fmt(s.accessibleMin!)} $unit';
    if (s.accessibleMax != null) return '≤ ${_fmt(s.accessibleMax!)} $unit';
    return '';
  }

  Widget _buildAddObjectButton() {
    return GestureDetector(
      onTap: _addObject,
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.symmetric(vertical: 14),
        decoration: BoxDecoration(
          color: AppColors.primary.withValues(alpha: 0.04),
          borderRadius: BorderRadius.circular(14),
          border: Border.all(
            color: AppColors.primary.withValues(alpha: 0.4),
            width: 1.5,
          ),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.add_circle_outline,
                size: 18, color: AppColors.primary),
            const SizedBox(width: 8),
            Text(
              'Add Object',
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w700,
                color: AppColors.primary,
              ),
            ),
          ],
        ),
      ),
    );
  }

  static String _fmt(double v) =>
      v == v.roundToDouble() ? v.toStringAsFixed(0) : v.toString();
}
