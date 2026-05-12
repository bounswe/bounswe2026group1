import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:image_picker/image_picker.dart';
import 'package:provider/provider.dart';
import '../theme/app_colors.dart';
import '../services/auth_service.dart';
import '../services/api_service.dart';
import '../models/report_model.dart';
import '../widgets/badge_chip.dart';
import '../main.dart' show AuthShell;
import 'avatar_crop_screen.dart';
import 'leaderboard_screen.dart';
import 'report_detail_screen.dart';
import 'onboarding_tutorial_screen.dart';
import 'routing_preferences_screen.dart';

class ProfileScreen extends StatefulWidget {
  final void Function(int)? onTabSwitch;

  const ProfileScreen({super.key, this.onTabSwitch});

  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen> {
  Map<String, dynamic>? _userInfo;
  List<ReportModel> _userReports = [];
  bool _loading = true;
  String? _error;

  // ── Edit mode state ────────────────────────────────────────────────────────
  bool _editMode = false;
  bool _saving = false;
  final TextEditingController _nameController = TextEditingController();
  final TextEditingController _bioController = TextEditingController();
  final _formKey = GlobalKey<FormState>();
  File? _pickedAvatar;

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  @override
  void dispose() {
    _nameController.dispose();
    _bioController.dispose();
    super.dispose();
  }

  Future<void> _loadData() async {
    final auth = context.read<AuthService>();
    if (!auth.isAuthenticated) {
      setState(() => _loading = false);
      return;
    }
    try {
      final api = auth.api;
      final userInfo = await api.getMyProfile();
      final reports = await api.getReportsByUser(auth.userId);
      if (!mounted) return;
      setState(() {
        _userInfo = userInfo;
        _userReports = reports;
        _loading = false;
      });
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e.toString();
          _loading = false;
        });
      }
    }
  }

  void _enterEditMode() {
    setState(() {
      _nameController.text =
          (_userInfo?['name'] ?? _userInfo?['fullName'] ?? '') as String;
      _bioController.text = (_userInfo?['bio'] ?? '') as String;
      _pickedAvatar = null;
      _editMode = true;
    });
  }

  void _cancelEdit() {
    setState(() {
      _editMode = false;
      _pickedAvatar = null;
    });
  }

  Future<void> _pickAvatar() async {
    try {
      final picker = ImagePicker();
      final picked = await picker.pickImage(
        source: ImageSource.gallery,
        // Wider source so the user has room to crop without losing detail.
        maxWidth: 2048,
        maxHeight: 2048,
        imageQuality: 90,
      );
      if (picked == null) return;
      if (!mounted) return;
      // Hand off to the in-app cropper. It returns a square PNG file we can
      // upload as-is, or null when the user cancels.
      final cropped = await Navigator.push<File>(
        context,
        MaterialPageRoute(
          fullscreenDialog: true,
          builder: (_) => AvatarCropScreen(source: File(picked.path)),
        ),
      );
      if (!mounted || cropped == null) return;
      setState(() => _pickedAvatar = cropped);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Could not pick image: $e')),
        );
      }
    }
  }

  /// Drops the avatar — locally if the user has only picked-but-not-saved a
  /// new file, or remotely (DELETE /api/users/{id}/profile/avatar) if there
  /// was an avatar persisted on the server.
  Future<void> _removeAvatar() async {
    final hadServerAvatar =
        ((_userInfo?['avatarUrl'] as String?) ?? '').isNotEmpty;

    // If only a freshly picked file is set, just clear it locally — nothing
    // hit the server yet.
    if (_pickedAvatar != null && !hadServerAvatar) {
      setState(() => _pickedAvatar = null);
      return;
    }

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: const Text('Remove profile photo?'),
        content: const Text('Your avatar will be cleared for everyone.'),
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
            child: const Text('Remove'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;

    setState(() => _saving = true);
    try {
      final auth = context.read<AuthService>();
      await auth.api.deleteAvatar(auth.userId);
      // Refresh so the avatarUrl in _userInfo clears.
      final updated = await auth.api.getMyProfile();
      if (!mounted) return;
      setState(() {
        if (updated != null) _userInfo = updated;
        _pickedAvatar = null;
        _saving = false;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Profile photo removed.')),
      );
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() => _saving = false);
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(e.userMessage)));
    } catch (e) {
      if (!mounted) return;
      setState(() => _saving = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Could not remove photo: $e')),
      );
    }
  }

  Future<void> _saveProfile() async {
    if (!_formKey.currentState!.validate()) return;

    final auth = context.read<AuthService>();
    final api = auth.api;
    final newName = _nameController.text.trim();
    final newBio = _bioController.text.trim();
    final currentName =
        (_userInfo?['name'] ?? _userInfo?['fullName'] ?? '') as String;
    final currentBio = (_userInfo?['bio'] ?? '') as String;

    setState(() => _saving = true);
    try {
      Map<String, dynamic>? updated;
      if (newName != currentName || newBio != currentBio) {
        updated = await api.updateUserProfile(
          userId: auth.userId,
          name: newName != currentName ? newName : null,
          bio: newBio != currentBio ? newBio : null,
        );
      }
      if (_pickedAvatar != null) {
        await api.uploadAvatar(auth.userId, _pickedAvatar!);
        // Refresh full profile to pick up new avatarUrl
        updated = await api.getMyProfile();
      }
      if (!mounted) return;
      setState(() {
        if (updated != null) _userInfo = updated;
        _editMode = false;
        _pickedAvatar = null;
        _saving = false;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Profile updated')),
      );
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() => _saving = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(e.userMessage)),
      );
    } catch (e) {
      if (!mounted) return;
      setState(() => _saving = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Could not update profile: $e')),
      );
    }
  }

  void _showLoginPrompt() {
    showDialog(
      context: context,
      builder: (_) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: Text(
          'Sign In Required',
          style: TextStyle(
            fontFamily: 'Plus Jakarta Sans',
            fontWeight: FontWeight.w700,
            color: AppColors.onSurface,
          ),
        ),
        content: Text(
          'You need to log in to access this feature.',
          style: TextStyle(color: AppColors.onSurfaceVariant),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text('Cancel', style: TextStyle(color: AppColors.outline)),
          ),
          TextButton(
            onPressed: () {
              Navigator.pop(context);
              Navigator.pushReplacement(
                context,
                MaterialPageRoute(builder: (_) => const AuthShell()),
              );
            },
            child: Text(
              'Sign In',
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

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthService>();

    return Scaffold(
      backgroundColor: AppColors.surfaceTint,
      body: SafeArea(
        child: _loading
            ? Center(
                child: CircularProgressIndicator(color: AppColors.primary),
              )
            : auth.isAuthenticated
                ? (_editMode ? _buildEditView() : _buildAuthenticatedView())
                : _buildGuestView(),
      ),
    );
  }

  // ── Authenticated View ─────────────────────────────────────────────────────

  Widget _buildAuthenticatedView() {
    if (_error != null) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.wifi_off, color: AppColors.outlineVariant, size: 40),
            const SizedBox(height: 12),
            Text(_error!, style: TextStyle(color: AppColors.onSurfaceVariant)),
            const SizedBox(height: 16),
            GestureDetector(
              onTap: _loadData,
              child: Text('Retry', style: TextStyle(color: AppColors.primary, fontWeight: FontWeight.w700)),
            ),
          ],
        ),
      );
    }

    final name = (_userInfo?['name'] ?? _userInfo?['fullName'] ?? 'User') as String;
    final email = (_userInfo?['email'] ?? '') as String;
    final bio = (_userInfo?['bio'] ?? '') as String;
    final avatarUrl = _userInfo?['avatarUrl'] as String?;
    final stats = _userInfo?['contributionStats'] as Map<String, dynamic>?;
    final reportsSubmitted = stats?['reportsSubmitted'] ?? _userReports.length;
    final routesPlanned = stats?['routesPlanned'] ?? 0;
    final points = (_userInfo?['points'] as num?)?.toInt() ?? 0;
    final rank = _userInfo?['rank'] as int?;
    final leaderboardHidden = (_userInfo?['leaderboardHidden'] as bool?) ?? false;
    final badges = (_userInfo?['badges'] as List<dynamic>?)
            ?.map((e) => e as String)
            .toList() ??
        const <String>[];
    final rankLabel = rank != null
        ? '#$rank'
        : leaderboardHidden
            ? 'Hidden'
            : '—';

    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(20, 8, 20, 24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _buildProfileHero(
            name: name,
            email: email,
            bio: bio,
            avatarUrl: avatarUrl,
          ),

          const SizedBox(height: 18),

          // Stats cards — REPORTS + ROUTES on the first row, POINTS + RANK on
          // the second row. Wrap so both rows reflow to a single column on
          // narrow phones without overflowing.
          Row(
            children: [
              Expanded(
                child: _buildStatCard(
                  icon: Icons.assignment_outlined,
                  label: 'REPORTS',
                  value: '$reportsSubmitted',
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: _buildStatCard(
                  icon: Icons.route_outlined,
                  label: 'ROUTES',
                  value: '$routesPlanned',
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: _buildStatCard(
                  icon: Icons.star_outline,
                  label: 'POINTS',
                  value: '$points',
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: _buildStatCard(
                  icon: Icons.leaderboard_outlined,
                  label: 'RANK',
                  value: rankLabel,
                ),
              ),
            ],
          ),

          if (badges.isNotEmpty) ...[
            const SizedBox(height: 20),
            Text(
              'Badges',
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
            ),
            const SizedBox(height: 8),
            BadgeList(badges: badges),
          ],

          const SizedBox(height: 16),
          OutlinedButton.icon(
            onPressed: () {
              Navigator.of(context).push(
                MaterialPageRoute(builder: (_) => const LeaderboardScreen()),
              );
            },
            icon: const Icon(Icons.leaderboard_outlined),
            label: const Text('View leaderboard'),
            style: OutlinedButton.styleFrom(
              minimumSize: const Size.fromHeight(44),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(12),
              ),
            ),
          ),

          const SizedBox(height: 16),
          _buildLeaderboardVisibilityToggle(leaderboardHidden),

          const SizedBox(height: 28),

          // Recent Reports
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'Recent Reports',
                style: TextStyle(
                  fontFamily: 'Plus Jakarta Sans',
                  fontWeight: FontWeight.w700,
                  fontSize: 18,
                  color: AppColors.onSurface,
                ),
              ),
            ],
          ),

          const SizedBox(height: 12),

          if (_userReports.isEmpty)
            Container(
              padding: const EdgeInsets.all(24),
              decoration: BoxDecoration(
                color: AppColors.cardSurface,
                borderRadius: BorderRadius.circular(16),
              ),
              child: Center(
                child: Text(
                  'No reports yet. Start contributing!',
                  style: TextStyle(color: AppColors.onSurfaceVariant, fontSize: 14),
                ),
              ),
            )
          else
            ...(_userReports.toList()
                  ..sort((a, b) {
                    try {
                      return DateTime.parse(b.publishDate)
                          .compareTo(DateTime.parse(a.publishDate));
                    } catch (_) {
                      return 0;
                    }
                  }))
                .map((report) => _buildReportItem(report)),
        ],
      ),
    );
  }

  // ── Edit View ──────────────────────────────────────────────────────────────

  Widget _buildEditView() {
    final avatarUrl = _userInfo?['avatarUrl'] as String?;

    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(20, 8, 20, 24),
      child: Form(
        key: _formKey,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Semantics(
                  button: true,
                  label: 'Cancel editing',
                  child: IconButton(
                    icon: Icon(Icons.arrow_back, color: AppColors.onSurface),
                    onPressed: _saving ? null : _cancelEdit,
                  ),
                ),
                const SizedBox(width: 4),
                Text(
                  'Edit Profile',
                  style: TextStyle(
                    fontFamily: 'Plus Jakarta Sans',
                    fontWeight: FontWeight.w700,
                    fontSize: 20,
                    color: AppColors.onSurface,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            Center(
              child: Column(
                children: [
                  _buildAvatar(
                    avatarUrl: avatarUrl,
                    localFile: _pickedAvatar,
                    semanticName: _nameController.text.isEmpty
                        ? 'User'
                        : _nameController.text,
                    onTap: _saving ? null : _pickAvatar,
                  ),
                  const SizedBox(height: 8),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      TextButton.icon(
                        onPressed: _saving ? null : _pickAvatar,
                        icon: Icon(Icons.image_outlined,
                            size: 18, color: AppColors.primary),
                        label: Text(
                          'Change picture',
                          style: TextStyle(
                            color: AppColors.primary,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ),
                      // Only show Remove when there's something to remove —
                      // either a local pick that hasn't been saved, or an
                      // existing avatar on the server.
                      if (_pickedAvatar != null ||
                          (avatarUrl != null && avatarUrl.isNotEmpty)) ...[
                        const SizedBox(width: 4),
                        TextButton.icon(
                          onPressed: _saving ? null : _removeAvatar,
                          icon: Icon(Icons.delete_outline,
                              size: 18, color: AppColors.error),
                          label: Text(
                            'Remove',
                            style: TextStyle(
                              color: AppColors.error,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ),
                      ],
                    ],
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12),
            Semantics(
              label: 'Display name',
              textField: true,
              child: TextFormField(
                controller: _nameController,
                enabled: !_saving,
                maxLength: 50,
                inputFormatters: [LengthLimitingTextInputFormatter(50)],
                decoration: InputDecoration(
                  labelText: 'Display name',
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                  ),
                ),
                validator: (v) {
                  final t = (v ?? '').trim();
                  if (t.length < 2) return 'Name must be at least 2 characters';
                  if (t.length > 50) return 'Name must be at most 50 characters';
                  return null;
                },
              ),
            ),
            const SizedBox(height: 8),
            Semantics(
              label: 'Bio',
              textField: true,
              child: TextFormField(
                controller: _bioController,
                enabled: !_saving,
                minLines: 3,
                maxLines: 5,
                maxLength: 500,
                inputFormatters: [LengthLimitingTextInputFormatter(500)],
                decoration: InputDecoration(
                  labelText: 'Bio',
                  alignLabelWithHint: true,
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                  ),
                ),
                validator: (v) {
                  if ((v ?? '').length > 500) {
                    return 'Bio must be at most 500 characters';
                  }
                  return null;
                },
              ),
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: _saving ? null : _cancelEdit,
                    style: OutlinedButton.styleFrom(
                      padding: const EdgeInsets.symmetric(vertical: 16),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                    ),
                    child: const Text('Cancel'),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Semantics(
                    button: true,
                    label: 'Save profile changes',
                    child: ElevatedButton(
                      onPressed: _saving ? null : _saveProfile,
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppColors.primary,
                        foregroundColor: AppColors.onPrimary,
                        padding: const EdgeInsets.symmetric(vertical: 16),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12),
                        ),
                      ),
                      child: _saving
                          ? SizedBox(
                              width: 20,
                              height: 20,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: AppColors.onPrimary,
                              ),
                            )
                          : const Text(
                              'Save',
                              style: TextStyle(fontWeight: FontWeight.w700),
                            ),
                    ),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildAvatar({
    String? avatarUrl,
    String semanticName = 'User',
    bool showBadge = true,
    File? localFile,
    VoidCallback? onTap,
  }) {
    Widget inner;
    if (localFile != null) {
      inner = ClipOval(
        child: Image.file(
          localFile,
          width: 90,
          height: 90,
          fit: BoxFit.cover,
        ),
      );
    } else if (avatarUrl != null && avatarUrl.isNotEmpty) {
      inner = ClipOval(
        child: Image.network(
          avatarUrl,
          width: 90,
          height: 90,
          fit: BoxFit.cover,
          errorBuilder: (_, __, ___) =>
              Icon(Icons.person, size: 44, color: AppColors.secondary),
        ),
      );
    } else {
      inner = Icon(Icons.person, size: 44, color: AppColors.secondary);
    }

    final avatar = Container(
      width: 90,
      height: 90,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: AppColors.surfaceContainerHigh,
        border: Border.all(color: AppColors.cardSurface, width: 3),
        boxShadow: [
          BoxShadow(
            color: AppColors.shadow,
            blurRadius: 12,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: ClipOval(child: Center(child: inner)),
    );

    final stack = Stack(
      children: [
        avatar,
        if (showBadge)
          Positioned(
            bottom: 2,
            right: 2,
            child: Container(
              width: 24,
              height: 24,
              decoration: BoxDecoration(
                color: AppColors.primary,
                shape: BoxShape.circle,
              ),
              child: Icon(
                onTap != null ? Icons.camera_alt : Icons.verified,
                color: AppColors.onPrimarySolid,
                size: 14,
              ),
            ),
          ),
      ],
    );

    final wrapped = Semantics(
      label: onTap != null ? 'Change profile picture' : 'Profile picture',
      value: semanticName,
      button: onTap != null,
      image: onTap == null,
      child: stack,
    );

    if (onTap == null) return wrapped;
    return GestureDetector(onTap: onTap, child: wrapped);
  }

  Widget _buildStatCard({
    required IconData icon,
    required String label,
    required String value,
  }) {
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 18, 16, 16),
      decoration: BoxDecoration(
        color: AppColors.cardSurface,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(
          color: AppColors.outlineVariant.withValues(alpha: 0.4),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 28,
                height: 28,
                decoration: BoxDecoration(
                  color: AppColors.primary.withValues(alpha: 0.12),
                  shape: BoxShape.circle,
                ),
                alignment: Alignment.center,
                child: Icon(icon, size: 14, color: AppColors.primary),
              ),
              const SizedBox(width: 8),
              Text(
                label,
                style: TextStyle(
                  fontSize: 10,
                  fontWeight: FontWeight.w800,
                  letterSpacing: 1.2,
                  color: AppColors.onSurfaceVariant,
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Text(
            value,
            style: TextStyle(
              fontFamily: 'Plus Jakarta Sans',
              fontWeight: FontWeight.w800,
              fontSize: 28,
              height: 1,
              color: AppColors.onSurface,
            ),
          ),
        ],
      ),
    );
  }

  /// Opt-out toggle for the public leaderboard. Flipping it preserves the
  /// user's accumulated points on the backend — they re-enter the ranking
  /// instantly when this flips back.
  Widget _buildLeaderboardVisibilityToggle(bool hidden) {
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
      decoration: BoxDecoration(
        color: AppColors.cardSurface,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(
          color: AppColors.outlineVariant.withValues(alpha: 0.4),
        ),
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Hide me from the public leaderboard',
                  style: TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                    color: AppColors.onSurface,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  'Your points stay accurate either way.',
                  style: TextStyle(
                    fontSize: 12,
                    color: AppColors.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
          Switch.adaptive(
            value: hidden,
            onChanged: _saving ? null : _toggleLeaderboardVisibility,
          ),
        ],
      ),
    );
  }

  Future<void> _toggleLeaderboardVisibility(bool nextHidden) async {
    final api = context.read<AuthService>().api;
    setState(() => _saving = true);
    try {
      final updated = await api.setLeaderboardVisibility(nextHidden);
      if (!mounted) return;
      setState(() {
        _userInfo = updated;
        _saving = false;
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text(nextHidden
              ? 'You are now hidden from the public leaderboard.'
              : 'You are visible on the public leaderboard.'),
        ));
      }
    } catch (e) {
      if (!mounted) return;
      setState(() => _saving = false);
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text('Failed to update visibility: ${_userMessage(e)}'),
      ));
    }
  }

  String _userMessage(Object e) =>
      e is ApiException ? e.userMessage : e.toString();

  /// Hero card with a soft brand-tinted gradient and the avatar nested
  /// inside. Replaces the previous flat avatar+text stack and gives the
  /// profile page a clearer focal point.
  Widget _buildProfileHero({
    required String name,
    required String email,
    required String bio,
    String? avatarUrl,
  }) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.fromLTRB(20, 24, 20, 24),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [
            AppColors.primary.withValues(alpha: 0.10),
            AppColors.successContainer.withValues(alpha: 0.45),
          ],
        ),
        borderRadius: BorderRadius.circular(24),
        border: Border.all(
          color: AppColors.primary.withValues(alpha: 0.18),
        ),
      ),
      child: Column(
        children: [
          _buildAvatar(avatarUrl: avatarUrl, semanticName: name),
          const SizedBox(height: 14),
          Semantics(
            label: 'Display name',
            value: name,
            child: Text(
              name,
              textAlign: TextAlign.center,
              style: TextStyle(
                fontFamily: 'Plus Jakarta Sans',
                fontWeight: FontWeight.w800,
                fontSize: 22,
                color: AppColors.onSurface,
              ),
            ),
          ),
          if (email.isNotEmpty) ...[
            const SizedBox(height: 6),
            Semantics(
              label: 'Email',
              value: email,
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const SizedBox(width: 6),
                  Flexible(
                    child: Text(
                      email,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                        color: AppColors.onSurface,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
          if (bio.isNotEmpty) ...[
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.symmetric(
                  horizontal: 14, vertical: 10),
              decoration: BoxDecoration(
                color: AppColors.cardSurface.withValues(alpha: 0.85),
                borderRadius: BorderRadius.circular(14),
              ),
              child: Semantics(
                label: 'Bio',
                value: bio,
                child: Text(
                  bio,
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontSize: 13,
                    color: AppColors.onSurface,
                    height: 1.4,
                  ),
                ),
              ),
            ),
          ],
          const SizedBox(height: 14),
          Row(
            children: [
              Expanded(
                child: _heroPillButton(
                  icon: Icons.edit_outlined,
                  label: 'Edit profile',
                  onTap: _enterEditMode,
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: _heroPillButton(
                  icon: Icons.tune,
                  label: 'Preferences',
                  onTap: _openPreferences,
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          SizedBox(
            width: double.infinity,
            child: _heroPillButton(
              icon: Icons.school_outlined,
              label: 'Replay tutorial',
              onTap: _replayTutorial,
            ),
          ),
        ],
      ),
    );
  }

  void _replayTutorial() {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => const OnboardingTutorialScreen(),
        fullscreenDialog: true,
      ),
    );
  }

  Widget _heroPillButton({
    required IconData icon,
    required String label,
    required VoidCallback onTap,
  }) {
    return OutlinedButton.icon(
      onPressed: onTap,
      icon: Icon(icon, size: 16, color: AppColors.primary),
      label: Text(
        label,
        style: TextStyle(
          fontWeight: FontWeight.w700,
          color: AppColors.primary,
        ),
      ),
      style: OutlinedButton.styleFrom(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        side: BorderSide(color: AppColors.primary.withValues(alpha: 0.5)),
        shape: const StadiumBorder(),
        backgroundColor: AppColors.cardSurface,
      ),
    );
  }

  void _openPreferences() {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => const RoutingPreferencesScreen(),
      ),
    );
  }

  Widget _buildReportItem(ReportModel report) {
    // Mirrors UserProfileScreen's tile so the self/other profile look stays
    // consistent: per-object-type icon tint, status pill on the right.
    final isFixed = report.status == ReportStatus.fixed;
    final isVerified = report.status == ReportStatus.verified;
    final pillBg = isFixed
        ? AppColors.infoContainer
        : isVerified
            ? AppColors.successContainer
            : AppColors.surfaceContainer;
    final pillFg = isFixed
        ? AppColors.info
        : isVerified
            ? AppColors.success
            : AppColors.onSurfaceVariant;
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Material(
        color: AppColors.cardSurface,
        borderRadius: BorderRadius.circular(14),
        child: InkWell(
          borderRadius: BorderRadius.circular(14),
          onTap: () => Navigator.push(
            context,
            MaterialPageRoute(
              builder: (_) => ReportDetailScreen(report: report),
            ),
          ),
          child: Padding(
            padding:
                const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
            child: Row(
              children: [
                Container(
                  width: 36,
                  height: 36,
                  decoration: BoxDecoration(
                    color: report.displayColor.withValues(alpha: 0.14),
                    shape: BoxShape.circle,
                  ),
                  alignment: Alignment.center,
                  child: Icon(report.displayIcon,
                      color: report.displayColor, size: 18),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        report.headline,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontWeight: FontWeight.w700,
                          fontSize: 14,
                          color: AppColors.onSurface,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        '${report.timeAgo} · ${report.status.label}',
                        style: TextStyle(
                          fontSize: 11,
                          color: AppColors.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(
                      horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: pillBg,
                    borderRadius: BorderRadius.circular(999),
                  ),
                  child: Text(
                    report.status.label.toUpperCase(),
                    style: TextStyle(
                      fontSize: 9,
                      fontWeight: FontWeight.w800,
                      letterSpacing: 0.6,
                      color: pillFg,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  // ── Guest View ─────────────────────────────────────────────────────────────

  Widget _buildGuestView() {
    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(20, 8, 20, 24),
      child: Column(
        children: [
          const SizedBox(height: 32),

          // Avatar
          Center(
            child: Column(
              children: [
                Container(
                  width: 90,
                  height: 90,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: AppColors.surfaceContainerHigh,
                    border:
                        Border.all(color: AppColors.cardSurface, width: 3),
                    boxShadow: [
                      BoxShadow(
                        color: AppColors.shadow,
                        blurRadius: 12,
                        offset: const Offset(0, 4),
                      ),
                    ],
                  ),
                  child: Icon(Icons.person_outline, size: 44, color: AppColors.secondary),
                ),
                const SizedBox(height: 14),
                Text(
                  'Guest Account',
                  style: TextStyle(
                    fontFamily: 'Plus Jakarta Sans',
                    fontWeight: FontWeight.w700,
                    fontSize: 22,
                    color: AppColors.onSurface,
                  ),
                ),
                const SizedBox(height: 6),
                Text(
                  'Sign in to access your full profile',
                  style: TextStyle(
                    fontSize: 14,
                    color: AppColors.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),

          const SizedBox(height: 36),

          // What you're missing
          Container(
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              color: AppColors.cardSurface,
              borderRadius: BorderRadius.circular(20),
              boxShadow: [
                BoxShadow(
                  color: AppColors.shadow,
                  blurRadius: 10,
                  offset: const Offset(0, 2),
                ),
              ],
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'With an account you can:',
                  style: TextStyle(
                    fontFamily: 'Plus Jakarta Sans',
                    fontWeight: FontWeight.w700,
                    fontSize: 15,
                    color: AppColors.onSurface,
                  ),
                ),
                const SizedBox(height: 16),
                _buildFeatureRow(Icons.flag_outlined, 'Submit and manage reports'),
                _buildFeatureRow(Icons.thumb_up_outlined, 'Agree or disagree on reports'),
                _buildFeatureRow(Icons.tune_outlined, 'Set accessibility preferences'),
                _buildFeatureRow(Icons.history, 'View your contribution history'),
              ],
            ),
          ),

          const SizedBox(height: 28),

          // Sign In button
          GestureDetector(
            onTap: () => Navigator.pushReplacement(
              context,
              MaterialPageRoute(builder: (_) => const AuthShell()),
            ),
            child: Container(
              width: double.infinity,
              padding: const EdgeInsets.symmetric(vertical: 18),
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [AppColors.primary, AppColors.primaryDim],
                ),
                borderRadius: BorderRadius.circular(14),
                boxShadow: [
                  BoxShadow(
                    color: AppColors.primary.withOpacity(0.25),
                    blurRadius: 20,
                    offset: const Offset(0, 6),
                  ),
                ],
              ),
              child: Text(
                'Sign In',
                textAlign: TextAlign.center,
                style: TextStyle(
                  fontFamily: 'Plus Jakarta Sans',
                  fontWeight: FontWeight.w700,
                  fontSize: 16,
                  color: AppColors.onPrimary,
                ),
              ),
            ),
          ),

          const SizedBox(height: 14),

          // Register link
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text(
                "Don't have an account? ",
                style: TextStyle(fontSize: 14, color: AppColors.onSurfaceVariant),
              ),
              GestureDetector(
                onTap: () => Navigator.pushReplacement(
                  context,
                  MaterialPageRoute(builder: (_) => const AuthShell(initialTab: 1)),
                ),
                child: Text(
                  'Sign Up',
                  style: TextStyle(
                    fontSize: 14,
                    color: AppColors.primary,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildFeatureRow(IconData icon, String text) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Row(
        children: [
          Container(
            width: 32,
            height: 32,
            decoration: BoxDecoration(
              color: AppColors.primary.withOpacity(0.1),
              shape: BoxShape.circle,
            ),
            child: Icon(icon, color: AppColors.primary, size: 16),
          ),
          const SizedBox(width: 12),
          Text(
            text,
            style: TextStyle(
              fontSize: 14,
              color: AppColors.onSurface,
              fontWeight: FontWeight.w500,
            ),
          ),
        ],
      ),
    );
  }

}