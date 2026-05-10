import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../models/routing_preferences.dart';
import '../services/api_service.dart';
import '../services/auth_service.dart';
import '../theme/app_colors.dart';

/// Settings page for the user's routing preferences. Mirrors the web's
/// [RoutingPreferencesSection] flow: pick a built-in or custom preset, view
/// its details, or create your own (up to [_maxCustomProfiles]).
///
/// All mutations are immediate — tapping a card activates that preset, the
/// editor sheet POSTs / PUTs on Save, the delete action DELETEs straight
/// away. There's no separate "Save preferences" button.
class RoutingPreferencesScreen extends StatefulWidget {
  const RoutingPreferencesScreen({super.key});

  @override
  State<RoutingPreferencesScreen> createState() =>
      _RoutingPreferencesScreenState();
}

const int _maxCustomProfiles = 5;

const Map<String, IconData> _presetIcons = {
  'NONE': Icons.do_not_disturb_alt_outlined,
  'WHEELCHAIR_USER': Icons.accessible,
  'BLIND_OR_LOW_VISION': Icons.visibility_outlined,
  'MOBILITY_LIMITED': Icons.elderly,
};

const Map<String, ({IconData icon, String label})> _travelModeLabels = {
  'WALKING': (icon: Icons.directions_walk, label: 'Walking'),
  'WHEELCHAIR': (icon: Icons.accessible_forward, label: 'Wheelchair'),
};

class _RoutingPreferencesScreenState extends State<RoutingPreferencesScreen> {
  RoutingPreferences? _prefs;
  bool _loading = true;
  bool _busy = false; // any mutation in flight
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  // ── Load / mutate ────────────────────────────────────────────────────────

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final prefs =
          await context.read<AuthService>().api.getRoutingPreferences();
      if (!mounted) return;
      setState(() {
        _prefs = prefs;
        _loading = false;
      });
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.userMessage;
        _loading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _error = 'Could not load preferences.';
        _loading = false;
      });
    }
  }

  Future<void> _withBusy(Future<void> Function() body, {String? errorPrefix}) async {
    if (_busy) return;
    setState(() => _busy = true);
    try {
      await body();
    } on ApiException catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('${errorPrefix ?? 'Failed'} — ${e.userMessage}')),
      );
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(errorPrefix ?? 'Something went wrong.')),
      );
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _activateBuiltIn(RoutingPresetInfo preset) async {
    await _withBusy(() async {
      final api = context.read<AuthService>().api;
      final updated =
          await api.updateRoutingPreferences(preset: preset.name);
      if (!mounted) return;
      setState(() => _prefs = _prefs?._copyForActivation(updated));
    }, errorPrefix: 'Could not activate preset');
  }

  Future<void> _activateCustom(CustomRoutingProfile profile) async {
    await _withBusy(() async {
      final api = context.read<AuthService>().api;
      final updated = await api.activateCustomRoutingProfile(profile.id);
      if (!mounted) return;
      setState(() => _prefs = updated);
    }, errorPrefix: 'Could not activate preset');
  }

  Future<void> _createCustom(_CustomEditorResult form) async {
    await _withBusy(() async {
      final api = context.read<AuthService>().api;
      await api.createCustomRoutingProfile(
        name: form.name,
        constraints: form.constraints,
        travelMode: form.travelMode,
      );
      // Re-load to pick up the new profile + recomputed counters.
      final prefs = await api.getRoutingPreferences();
      if (!mounted) return;
      setState(() => _prefs = prefs);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('"${form.name}" created.')),
      );
    }, errorPrefix: 'Could not create preset');
  }

  Future<void> _updateCustom(
    CustomRoutingProfile profile,
    _CustomEditorResult form,
  ) async {
    await _withBusy(() async {
      final api = context.read<AuthService>().api;
      await api.updateCustomRoutingProfile(
        id: profile.id,
        name: form.name,
        constraints: form.constraints,
        travelMode: form.travelMode,
      );
      final prefs = await api.getRoutingPreferences();
      if (!mounted) return;
      setState(() => _prefs = prefs);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('"${form.name}" updated.')),
      );
    }, errorPrefix: 'Could not update preset');
  }

  Future<void> _deleteCustom(CustomRoutingProfile profile) async {
    await _withBusy(() async {
      final api = context.read<AuthService>().api;
      await api.deleteCustomRoutingProfile(profile.id);
      final prefs = await api.getRoutingPreferences();
      if (!mounted) return;
      setState(() => _prefs = prefs);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('"${profile.name}" deleted.')),
      );
    }, errorPrefix: 'Could not delete preset');
  }

  // ── Sheet helpers ────────────────────────────────────────────────────────

  Future<void> _openBuiltInDetails(RoutingPresetInfo preset) async {
    final prefs = _prefs;
    if (prefs == null) return;
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => _PresetDetailsSheet(
        title: preset.label,
        subtitle: 'Built-in preset',
        constraintNames: preset.constraints,
        allConstraints: prefs.availableConstraints,
        travelMode: preset.defaultTravelMode,
      ),
    );
  }

  Future<void> _openCustomDetails(CustomRoutingProfile profile) async {
    final prefs = _prefs;
    if (prefs == null) return;
    final action = await showModalBottomSheet<_DetailsAction>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => _PresetDetailsSheet(
        title: profile.name,
        subtitle: 'Custom preset',
        constraintNames: profile.constraints,
        allConstraints: prefs.availableConstraints,
        travelMode: profile.preferredTravelMode,
        canEdit: true,
      ),
    );
    if (!mounted || action == null) return;
    switch (action) {
      case _DetailsAction.edit:
        await _openEditor(profile);
      case _DetailsAction.delete:
        await _confirmAndDelete(profile);
    }
  }

  Future<void> _confirmAndDelete(CustomRoutingProfile profile) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        shape:
            RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: const Text('Delete this preset?'),
        content: Text(
          '"${profile.name}" will be removed permanently. Your other presets are unaffected.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancel'),
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
    if (ok != true) return;
    await _deleteCustom(profile);
  }

  Future<void> _openEditor([CustomRoutingProfile? profile]) async {
    final prefs = _prefs;
    if (prefs == null) return;
    final result = await showModalBottomSheet<_CustomEditorResult>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => _CustomPresetEditorSheet(
        availableConstraints: prefs.availableConstraints,
        existing: profile,
      ),
    );
    if (!mounted || result == null) return;
    if (profile == null) {
      await _createCustom(result);
    } else {
      await _updateCustom(profile, result);
    }
  }

  // ── Build ──────────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.surfaceTint,
      body: SafeArea(
        child: Column(
          children: [
            _buildTopBar(),
            Expanded(child: _buildBody()),
          ],
        ),
      ),
    );
  }

  Widget _buildTopBar() {
    return Container(
      decoration: BoxDecoration(
        color: AppColors.surfaceTint,
        boxShadow: [
          BoxShadow(
            color: AppColors.shadow,
            blurRadius: 6,
            offset: const Offset(0, 3),
          ),
        ],
      ),
      padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 10),
      child: Row(
        children: [
          IconButton(
            icon: Icon(Icons.arrow_back, color: AppColors.primary),
            onPressed: () => Navigator.pop(context),
          ),
          Expanded(
            child: Center(
              child: Text(
                'Routing preferences',
                style: TextStyle(
                  fontFamily: 'Plus Jakarta Sans',
                  fontWeight: FontWeight.w800,
                  fontSize: 18,
                  color: AppColors.primary,
                ),
              ),
            ),
          ),
          const SizedBox(width: 48),
        ],
      ),
    );
  }

  Widget _buildBody() {
    if (_loading) {
      return Center(
        child: CircularProgressIndicator(color: AppColors.primary),
      );
    }
    if (_error != null) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.wifi_off, color: AppColors.outlineVariant, size: 40),
            const SizedBox(height: 12),
            Text(_error!,
                style: TextStyle(color: AppColors.onSurfaceVariant)),
            const SizedBox(height: 16),
            GestureDetector(
              onTap: _load,
              child: Text(
                'Retry',
                style: TextStyle(
                  color: AppColors.primary,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
          ],
        ),
      );
    }
    final prefs = _prefs!;
    final builtIns = prefs.availablePresets
        .where((p) => p.name != 'CUSTOM')
        .toList();
    final customs = prefs.customProfiles;
    final atCap = customs.length >= _maxCustomProfiles;
    return RefreshIndicator(
      onRefresh: _load,
      color: AppColors.primary,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(20, 16, 20, 24),
        children: [
          _buildIntroCard(),
          const SizedBox(height: 18),
          Row(
            children: [
              Text(
                'PRESETS',
                style: TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.w800,
                  letterSpacing: 1.2,
                  color: AppColors.onSurfaceVariant,
                ),
              ),
              const Spacer(),
              Text(
                '${customs.length}/$_maxCustomProfiles custom',
                style: TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.w700,
                  color: AppColors.onSurfaceVariant,
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          for (final preset in builtIns) ...[
            _BuiltInPresetCard(
              preset: preset,
              active: prefs.activeCustomProfileId == null &&
                  prefs.preferredPreset == preset.name,
              busy: _busy,
              onTap: () => _activateBuiltIn(preset),
              onInfo: () => _openBuiltInDetails(preset),
            ),
            const SizedBox(height: 8),
          ],
          for (final profile in customs) ...[
            _CustomPresetCard(
              profile: profile,
              active: prefs.activeCustomProfileId == profile.id,
              busy: _busy,
              onTap: () => _activateCustom(profile),
              onInfo: () => _openCustomDetails(profile),
            ),
            const SizedBox(height: 8),
          ],
          _CreatePresetTile(
            atCap: atCap,
            onTap: atCap ? null : () => _openEditor(),
          ),
        ],
      ),
    );
  }

  Widget _buildIntroCard() {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.successContainer.withValues(alpha: 0.6),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 36,
            height: 36,
            decoration: BoxDecoration(
              color: AppColors.success.withValues(alpha: 0.18),
              shape: BoxShape.circle,
            ),
            alignment: Alignment.center,
            child: Icon(Icons.tune, color: AppColors.success, size: 18),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              'Pick a preset to shape every accessible route. Built-in presets cover '
              'common needs; you can also save up to $_maxCustomProfiles of your own.',
              style: TextStyle(
                fontSize: 12,
                color: AppColors.onSurface,
                height: 1.45,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

// ── Preset card extension helper ─────────────────────────────────────────

extension on RoutingPreferences {
  /// Patches `preferredPreset` + `constraints` + `preferredTravelMode` /
  /// `activeCustomProfileId` from a freshly returned [other], while keeping
  /// the catalogues + customProfiles list (which the PUT endpoint also
  /// echoes back unchanged).
  RoutingPreferences _copyForActivation(RoutingPreferences other) {
    return RoutingPreferences(
      preferredPreset: other.preferredPreset,
      constraints: other.constraints,
      preferredTravelMode: other.preferredTravelMode,
      activeCustomProfileId: other.activeCustomProfileId,
      availablePresets: availablePresets,
      availableConstraints: availableConstraints,
      customProfiles: other.customProfiles.isNotEmpty
          ? other.customProfiles
          : customProfiles,
    );
  }
}

// ── Preset cards ─────────────────────────────────────────────────────────

class _BuiltInPresetCard extends StatelessWidget {
  final RoutingPresetInfo preset;
  final bool active;
  final bool busy;
  final VoidCallback onTap;
  final VoidCallback onInfo;

  const _BuiltInPresetCard({
    required this.preset,
    required this.active,
    required this.busy,
    required this.onTap,
    required this.onInfo,
  });

  @override
  Widget build(BuildContext context) {
    final icon = _presetIcons[preset.name] ?? Icons.route_outlined;
    return _PresetCardShell(
      active: active,
      busy: busy,
      onTap: onTap,
      onInfo: onInfo,
      icon: icon,
      title: preset.label,
      subtitle: null,
      constraintCount: preset.constraints.length,
      travelMode: preset.defaultTravelMode,
    );
  }
}

class _CustomPresetCard extends StatelessWidget {
  final CustomRoutingProfile profile;
  final bool active;
  final bool busy;
  final VoidCallback onTap;
  final VoidCallback onInfo;

  const _CustomPresetCard({
    required this.profile,
    required this.active,
    required this.busy,
    required this.onTap,
    required this.onInfo,
  });

  @override
  Widget build(BuildContext context) {
    return _PresetCardShell(
      active: active,
      busy: busy,
      onTap: onTap,
      onInfo: onInfo,
      icon: Icons.bookmark_outline,
      title: profile.name,
      subtitle: 'Custom',
      constraintCount: profile.constraints.length,
      travelMode: profile.preferredTravelMode,
    );
  }
}

class _PresetCardShell extends StatelessWidget {
  final bool active;
  final bool busy;
  final VoidCallback onTap;
  final VoidCallback onInfo;
  final IconData icon;
  final String title;
  final String? subtitle;
  final int constraintCount;
  final String? travelMode;

  const _PresetCardShell({
    required this.active,
    required this.busy,
    required this.onTap,
    required this.onInfo,
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.constraintCount,
    required this.travelMode,
  });

  @override
  Widget build(BuildContext context) {
    final tm = travelMode != null ? _travelModeLabels[travelMode!] : null;
    return Opacity(
      opacity: busy ? 0.7 : 1,
      child: Material(
        color: active
            ? AppColors.primary.withValues(alpha: 0.08)
            : AppColors.cardSurface,
        borderRadius: BorderRadius.circular(18),
        child: InkWell(
          borderRadius: BorderRadius.circular(18),
          onTap: busy ? null : onTap,
          child: Container(
            padding: const EdgeInsets.fromLTRB(14, 14, 8, 14),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(18),
              border: Border.all(
                color: active
                    ? AppColors.primary
                    : AppColors.outlineVariant.withValues(alpha: 0.5),
                width: active ? 2 : 1,
              ),
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.center,
              children: [
                Container(
                  width: 40,
                  height: 40,
                  decoration: BoxDecoration(
                    color: (active
                            ? AppColors.primary
                            : AppColors.onSurfaceVariant)
                        .withValues(alpha: 0.12),
                    shape: BoxShape.circle,
                  ),
                  alignment: Alignment.center,
                  child: Icon(
                    icon,
                    color: active
                        ? AppColors.primary
                        : AppColors.onSurfaceVariant,
                    size: 20,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Flexible(
                            child: Text(
                              title,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: TextStyle(
                                fontFamily: 'Plus Jakarta Sans',
                                fontWeight: FontWeight.w800,
                                fontSize: 14,
                                color: AppColors.onSurface,
                              ),
                            ),
                          ),
                          if (subtitle != null) ...[
                            const SizedBox(width: 6),
                            Container(
                              padding: const EdgeInsets.symmetric(
                                  horizontal: 6, vertical: 1),
                              decoration: BoxDecoration(
                                color: AppColors.surfaceContainerHigh,
                                borderRadius: BorderRadius.circular(999),
                              ),
                              child: Text(
                                subtitle!,
                                style: TextStyle(
                                  fontSize: 9,
                                  fontWeight: FontWeight.w800,
                                  letterSpacing: 0.4,
                                  color: AppColors.onSurfaceVariant,
                                ),
                              ),
                            ),
                          ],
                          if (active) ...[
                            const SizedBox(width: 6),
                            Container(
                              padding: const EdgeInsets.symmetric(
                                  horizontal: 6, vertical: 1),
                              decoration: BoxDecoration(
                                color: AppColors.primary,
                                borderRadius: BorderRadius.circular(999),
                              ),
                              child: Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  Icon(Icons.check,
                                      size: 10,
                                      color: AppColors.onPrimarySolid),
                                  const SizedBox(width: 2),
                                  Text(
                                    'ACTIVE',
                                    style: TextStyle(
                                      fontSize: 9,
                                      fontWeight: FontWeight.w800,
                                      letterSpacing: 0.5,
                                      color: AppColors.onPrimarySolid,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ],
                      ),
                      const SizedBox(height: 4),
                      Row(
                        children: [
                          Text(
                            '$constraintCount '
                            '${constraintCount == 1 ? 'rule' : 'rules'}',
                            style: TextStyle(
                              fontSize: 11,
                              color: AppColors.onSurfaceVariant,
                            ),
                          ),
                          if (tm != null) ...[
                            const SizedBox(width: 10),
                            Icon(tm.icon,
                                size: 12,
                                color: AppColors.onSurfaceVariant),
                            const SizedBox(width: 3),
                            Text(
                              tm.label,
                              style: TextStyle(
                                fontSize: 11,
                                color: AppColors.onSurfaceVariant,
                              ),
                            ),
                          ],
                        ],
                      ),
                    ],
                  ),
                ),
                IconButton(
                  tooltip: 'Details',
                  iconSize: 20,
                  splashRadius: 18,
                  icon: Icon(Icons.info_outline,
                      color: AppColors.onSurfaceVariant),
                  onPressed: busy ? null : onInfo,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _CreatePresetTile extends StatelessWidget {
  final bool atCap;
  final VoidCallback? onTap;

  const _CreatePresetTile({required this.atCap, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final fg = atCap ? AppColors.outlineVariant : AppColors.primary;
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 16, horizontal: 14),
        decoration: BoxDecoration(
          color: AppColors.primary.withValues(alpha: atCap ? 0.02 : 0.05),
          borderRadius: BorderRadius.circular(18),
          border: Border.all(
            color: fg.withValues(alpha: 0.5),
            width: 1.5,
          ),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.add_circle_outline, size: 18, color: fg),
            const SizedBox(width: 8),
            Text(
              atCap ? 'Limit reached' : 'Create custom preset',
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w800,
                color: fg,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ── Details sheet ────────────────────────────────────────────────────────

enum _DetailsAction { edit, delete }

class _PresetDetailsSheet extends StatelessWidget {
  final String title;
  final String subtitle;
  final List<String> constraintNames;
  final List<RoutingConstraintInfo> allConstraints;
  final String? travelMode;
  final bool canEdit;

  const _PresetDetailsSheet({
    required this.title,
    required this.subtitle,
    required this.constraintNames,
    required this.allConstraints,
    required this.travelMode,
    this.canEdit = false,
  });

  @override
  Widget build(BuildContext context) {
    final selected = constraintNames.toSet();
    final picked = allConstraints.where((c) => selected.contains(c.name)).toList();
    final tm = travelMode != null ? _travelModeLabels[travelMode!] : null;
    return DraggableScrollableSheet(
      initialChildSize: 0.7,
      minChildSize: 0.45,
      maxChildSize: 0.92,
      expand: false,
      builder: (_, scrollController) => Container(
        decoration: BoxDecoration(
          color: AppColors.surfaceContainerLowest,
          borderRadius: const BorderRadius.vertical(
            top: Radius.circular(20),
          ),
        ),
        child: Column(
          children: [
            const SizedBox(height: 8),
            Container(
              width: 40,
              height: 4,
              decoration: BoxDecoration(
                color: AppColors.surfaceContainerHigh,
                borderRadius: BorderRadius.circular(999),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 14, 20, 6),
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          title,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            fontFamily: 'Plus Jakarta Sans',
                            fontWeight: FontWeight.w800,
                            fontSize: 18,
                            color: AppColors.onSurface,
                          ),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          subtitle,
                          style: TextStyle(
                            fontSize: 11,
                            fontWeight: FontWeight.w700,
                            letterSpacing: 0.4,
                            color: AppColors.onSurfaceVariant,
                          ),
                        ),
                      ],
                    ),
                  ),
                  IconButton(
                    icon: Icon(Icons.close, color: AppColors.onSurfaceVariant),
                    onPressed: () => Navigator.pop(context),
                  ),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              child: Row(
                children: [
                  _summaryChip(
                    icon: Icons.tune,
                    text: '${picked.length} '
                        '${picked.length == 1 ? 'rule' : 'rules'}',
                  ),
                  if (tm != null) ...[
                    const SizedBox(width: 8),
                    _summaryChip(icon: tm.icon, text: tm.label),
                  ],
                ],
              ),
            ),
            const SizedBox(height: 12),
            Divider(
              height: 1,
              thickness: 1,
              color: AppColors.surfaceContainerHigh,
            ),
            Expanded(
              child: picked.isEmpty
                  ? Center(
                      child: Padding(
                        padding: const EdgeInsets.all(24),
                        child: Text(
                          'No constraints — this preset uses default routing.',
                          textAlign: TextAlign.center,
                          style: TextStyle(
                            fontSize: 13,
                            fontStyle: FontStyle.italic,
                            color: AppColors.onSurfaceVariant,
                          ),
                        ),
                      ),
                    )
                  : ListView.separated(
                      controller: scrollController,
                      padding: const EdgeInsets.fromLTRB(20, 14, 20, 14),
                      itemCount: picked.length,
                      separatorBuilder: (_, __) => const SizedBox(height: 10),
                      itemBuilder: (_, i) => _constraintRow(picked[i]),
                    ),
            ),
            if (canEdit)
              Container(
                padding: const EdgeInsets.fromLTRB(20, 12, 20, 24),
                decoration: BoxDecoration(
                  color: AppColors.surfaceContainerLowest,
                  boxShadow: [
                    BoxShadow(
                      color: AppColors.shadow,
                      blurRadius: 12,
                      offset: const Offset(0, -2),
                    ),
                  ],
                ),
                child: Row(
                  children: [
                    Expanded(
                      child: OutlinedButton.icon(
                        onPressed: () =>
                            Navigator.pop(context, _DetailsAction.delete),
                        icon: Icon(Icons.delete_outline,
                            size: 18, color: AppColors.error),
                        label: Text(
                          'Delete',
                          style: TextStyle(
                            color: AppColors.error,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                        style: OutlinedButton.styleFrom(
                          padding:
                              const EdgeInsets.symmetric(vertical: 12),
                          side: BorderSide(
                              color: AppColors.error.withValues(alpha: 0.5)),
                          shape: const StadiumBorder(),
                        ),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      flex: 2,
                      child: FilledButton.icon(
                        onPressed: () =>
                            Navigator.pop(context, _DetailsAction.edit),
                        icon: const Icon(Icons.edit_outlined, size: 18),
                        label: const Text('Edit'),
                        style: FilledButton.styleFrom(
                          padding:
                              const EdgeInsets.symmetric(vertical: 12),
                          backgroundColor: AppColors.primary,
                          foregroundColor: AppColors.onPrimary,
                          shape: const StadiumBorder(),
                          textStyle: const TextStyle(
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _summaryChip({required IconData icon, required String text}) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainer,
        borderRadius: BorderRadius.circular(999),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 12, color: AppColors.onSurfaceVariant),
          const SizedBox(width: 6),
          Text(
            text,
            style: TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w700,
              color: AppColors.onSurface,
            ),
          ),
        ],
      ),
    );
  }

  Widget _constraintRow(RoutingConstraintInfo info) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainer,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.check_circle,
                  size: 14, color: AppColors.primary),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  info.label,
                  style: TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w700,
                    color: AppColors.onSurface,
                  ),
                ),
              ),
            ],
          ),
          if (info.description.isNotEmpty) ...[
            const SizedBox(height: 4),
            Padding(
              padding: const EdgeInsets.only(left: 22),
              child: Text(
                info.description,
                style: TextStyle(
                  fontSize: 11,
                  color: AppColors.onSurfaceVariant,
                  height: 1.4,
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

// ── Editor sheet ─────────────────────────────────────────────────────────

class _CustomEditorResult {
  final String name;
  final Set<String> constraints;
  final String? travelMode;
  const _CustomEditorResult({
    required this.name,
    required this.constraints,
    this.travelMode,
  });
}

class _CustomPresetEditorSheet extends StatefulWidget {
  final List<RoutingConstraintInfo> availableConstraints;
  final CustomRoutingProfile? existing;

  const _CustomPresetEditorSheet({
    required this.availableConstraints,
    this.existing,
  });

  @override
  State<_CustomPresetEditorSheet> createState() =>
      _CustomPresetEditorSheetState();
}

class _CustomPresetEditorSheetState extends State<_CustomPresetEditorSheet> {
  late final TextEditingController _nameController;
  late final Set<String> _constraints;
  String? _travelMode;
  String? _validationError;

  @override
  void initState() {
    super.initState();
    final existing = widget.existing;
    _nameController =
        TextEditingController(text: existing?.name ?? '');
    _constraints = {...?existing?.constraints};
    _travelMode = existing?.preferredTravelMode;
  }

  @override
  void dispose() {
    _nameController.dispose();
    super.dispose();
  }

  bool get _isEditing => widget.existing != null;

  void _toggleConstraint(String name) {
    setState(() {
      if (!_constraints.add(name)) _constraints.remove(name);
    });
  }

  void _setTravelMode(String? value) {
    setState(() => _travelMode = value);
  }

  void _save() {
    final name = _nameController.text.trim();
    if (name.isEmpty) {
      setState(() => _validationError = 'Give your preset a name.');
      return;
    }
    if (name.length > 50) {
      setState(() => _validationError = 'Name must be 50 characters or fewer.');
      return;
    }
    Navigator.pop(
      context,
      _CustomEditorResult(
        name: name,
        constraints: _constraints,
        travelMode: _travelMode,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return DraggableScrollableSheet(
      initialChildSize: 0.85,
      minChildSize: 0.5,
      maxChildSize: 0.95,
      expand: false,
      builder: (_, scrollController) => Container(
        decoration: BoxDecoration(
          color: AppColors.surfaceContainerLowest,
          borderRadius:
              const BorderRadius.vertical(top: Radius.circular(20)),
        ),
        child: Column(
          children: [
            const SizedBox(height: 8),
            Container(
              width: 40,
              height: 4,
              decoration: BoxDecoration(
                color: AppColors.surfaceContainerHigh,
                borderRadius: BorderRadius.circular(999),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 14, 20, 6),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      _isEditing
                          ? 'Edit custom preset'
                          : 'New custom preset',
                      style: TextStyle(
                        fontFamily: 'Plus Jakarta Sans',
                        fontWeight: FontWeight.w800,
                        fontSize: 18,
                        color: AppColors.onSurface,
                      ),
                    ),
                  ),
                  IconButton(
                    icon: Icon(Icons.close,
                        color: AppColors.onSurfaceVariant),
                    onPressed: () => Navigator.pop(context),
                  ),
                ],
              ),
            ),
            Divider(
              height: 1,
              thickness: 1,
              color: AppColors.surfaceContainerHigh,
            ),
            Expanded(
              child: ListView(
                controller: scrollController,
                padding: const EdgeInsets.fromLTRB(20, 18, 20, 16),
                children: [
                  _sectionLabel('NAME'),
                  const SizedBox(height: 8),
                  Container(
                    decoration: BoxDecoration(
                      color: AppColors.surfaceContainerLowest,
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(
                        color: AppColors.outlineVariant
                            .withValues(alpha: 0.5),
                      ),
                    ),
                    child: TextField(
                      controller: _nameController,
                      maxLength: 50,
                      style: TextStyle(
                          fontSize: 14, color: AppColors.onSurface),
                      decoration: InputDecoration(
                        hintText: 'e.g. Daily commute',
                        hintStyle: TextStyle(
                            color: AppColors.outlineVariant, fontSize: 13),
                        border: InputBorder.none,
                        contentPadding: const EdgeInsets.symmetric(
                            horizontal: 14, vertical: 12),
                        counterStyle: TextStyle(
                          fontSize: 11,
                          color: AppColors.outlineVariant,
                        ),
                      ),
                      onChanged: (_) =>
                          setState(() => _validationError = null),
                    ),
                  ),
                  const SizedBox(height: 22),
                  _sectionLabel('TRAVEL MODE'),
                  const SizedBox(height: 8),
                  _travelModeSegment(),
                  const SizedBox(height: 22),
                  _sectionLabel(
                    'CONSTRAINTS',
                    trailing: _constraints.isEmpty
                        ? null
                        : Text(
                            '${_constraints.length} selected',
                            style: TextStyle(
                              fontSize: 11,
                              fontWeight: FontWeight.w800,
                              color: AppColors.primary,
                            ),
                          ),
                  ),
                  const SizedBox(height: 8),
                  for (final c in widget.availableConstraints) ...[
                    _constraintRow(c),
                    const SizedBox(height: 6),
                  ],
                  if (_validationError != null) ...[
                    const SizedBox(height: 12),
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: AppColors.errorContainer,
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(
                            color: AppColors.errorContainerBorder),
                      ),
                      child: Row(
                        children: [
                          Icon(Icons.error_outline,
                              color: AppColors.onErrorContainer, size: 16),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              _validationError!,
                              style: TextStyle(
                                fontSize: 12,
                                color: AppColors.onErrorContainer,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ],
              ),
            ),
            Container(
              padding: const EdgeInsets.fromLTRB(20, 12, 20, 24),
              decoration: BoxDecoration(
                color: AppColors.surfaceContainerLowest,
                boxShadow: [
                  BoxShadow(
                    color: AppColors.shadow,
                    blurRadius: 12,
                    offset: const Offset(0, -2),
                  ),
                ],
              ),
              child: Row(
                children: [
                  Expanded(
                    child: OutlinedButton(
                      onPressed: () => Navigator.pop(context),
                      style: OutlinedButton.styleFrom(
                        padding: const EdgeInsets.symmetric(vertical: 12),
                        side: BorderSide(
                            color: AppColors.outlineVariant
                                .withValues(alpha: 0.6)),
                        shape: const StadiumBorder(),
                        foregroundColor: AppColors.onSurface,
                      ),
                      child: const Text(
                        'Cancel',
                        style: TextStyle(fontWeight: FontWeight.w700),
                      ),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    flex: 2,
                    child: FilledButton.icon(
                      onPressed: _save,
                      icon: const Icon(Icons.check_rounded, size: 18),
                      label: Text(_isEditing ? 'Save changes' : 'Create'),
                      style: FilledButton.styleFrom(
                        padding: const EdgeInsets.symmetric(vertical: 12),
                        backgroundColor: AppColors.primary,
                        foregroundColor: AppColors.onPrimary,
                        shape: const StadiumBorder(),
                        textStyle:
                            const TextStyle(fontWeight: FontWeight.w800),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _sectionLabel(String label, {Widget? trailing}) {
    return Row(
      children: [
        Text(
          label,
          style: TextStyle(
            fontSize: 11,
            fontWeight: FontWeight.w800,
            letterSpacing: 1.2,
            color: AppColors.onSurfaceVariant,
          ),
        ),
        const Spacer(),
        if (trailing != null) trailing,
      ],
    );
  }

  Widget _travelModeSegment() {
    final entries = <_TravelOption>[
      const _TravelOption(value: null, icon: Icons.public, label: 'Either'),
      _TravelOption(
        value: 'WALKING',
        icon: _travelModeLabels['WALKING']!.icon,
        label: _travelModeLabels['WALKING']!.label,
      ),
      _TravelOption(
        value: 'WHEELCHAIR',
        icon: _travelModeLabels['WHEELCHAIR']!.icon,
        label: _travelModeLabels['WHEELCHAIR']!.label,
      ),
    ];
    return Row(
      children: [
        for (int i = 0; i < entries.length; i++) ...[
          if (i > 0) const SizedBox(width: 8),
          Expanded(child: _travelModeButton(entries[i])),
        ],
      ],
    );
  }

  Widget _travelModeButton(_TravelOption opt) {
    final selected = _travelMode == opt.value;
    final fg = selected ? AppColors.primary : AppColors.onSurfaceVariant;
    return GestureDetector(
      onTap: () => _setTravelMode(opt.value),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 180),
        padding: const EdgeInsets.symmetric(vertical: 10),
        decoration: BoxDecoration(
          color: selected
              ? AppColors.primary.withValues(alpha: 0.08)
              : AppColors.cardSurface,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(
            color: selected ? AppColors.primary : AppColors.outlineVariant,
            width: selected ? 2 : 1,
          ),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(opt.icon, size: 16, color: fg),
            const SizedBox(width: 6),
            Text(
              opt.label,
              style: TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w700,
                color: fg,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _constraintRow(RoutingConstraintInfo info) {
    final selected = _constraints.contains(info.name);
    return InkWell(
      onTap: () => _toggleConstraint(info.name),
      borderRadius: BorderRadius.circular(14),
      child: Container(
        padding: const EdgeInsets.fromLTRB(12, 12, 12, 12),
        decoration: BoxDecoration(
          color: selected
              ? AppColors.primary.withValues(alpha: 0.06)
              : AppColors.cardSurface,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(
            color: selected
                ? AppColors.primary.withValues(alpha: 0.55)
                : AppColors.outlineVariant.withValues(alpha: 0.4),
          ),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.only(top: 2),
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 150),
                width: 20,
                height: 20,
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(6),
                  color: selected ? AppColors.primary : Colors.transparent,
                  border: Border.all(
                    color: selected
                        ? AppColors.primary
                        : AppColors.outlineVariant,
                    width: 2,
                  ),
                ),
                alignment: Alignment.center,
                child: selected
                    ? Icon(Icons.check,
                        size: 14, color: AppColors.onPrimarySolid)
                    : null,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    info.label,
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w700,
                      color: AppColors.onSurface,
                      height: 1.35,
                    ),
                  ),
                  if (info.description.isNotEmpty) ...[
                    const SizedBox(height: 2),
                    Text(
                      info.description,
                      style: TextStyle(
                        fontSize: 11,
                        color: AppColors.onSurfaceVariant,
                        height: 1.4,
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _TravelOption {
  final String? value;
  final IconData icon;
  final String label;
  const _TravelOption({
    required this.value,
    required this.icon,
    required this.label,
  });
}
