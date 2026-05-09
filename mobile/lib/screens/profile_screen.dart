import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:image_picker/image_picker.dart';
import 'package:provider/provider.dart';
import '../theme/app_colors.dart';
import '../services/auth_service.dart';
import '../services/api_service.dart';
import '../models/report_model.dart';
import '../main.dart' show AuthShell;
import 'report_detail_screen.dart';

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
      final userInfo = await api.getUserById(auth.userId);
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
        maxWidth: 1024,
        maxHeight: 1024,
        imageQuality: 85,
      );
      if (picked == null) return;
      setState(() => _pickedAvatar = File(picked.path));
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Could not pick image: $e')),
        );
      }
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
        updated = await api.getUserById(auth.userId);
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

    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(20, 8, 20, 24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Avatar + Name
          Center(
            child: Column(
              children: [
                _buildAvatar(avatarUrl: avatarUrl, semanticName: name),
                const SizedBox(height: 14),
                Semantics(
                  label: 'Display name',
                  value: name,
                  child: Text(
                    name,
                    style: TextStyle(
                      fontFamily: 'Plus Jakarta Sans',
                      fontWeight: FontWeight.w700,
                      fontSize: 22,
                      color: AppColors.onSurface,
                    ),
                  ),
                ),
                const SizedBox(height: 4),
                Semantics(
                  label: 'Email',
                  value: email,
                  child: Text(
                    email,
                    style: TextStyle(
                      fontSize: 14,
                      color: AppColors.onSurfaceVariant,
                    ),
                  ),
                ),
                if (bio.isNotEmpty) ...[
                  const SizedBox(height: 10),
                  Semantics(
                    label: 'Bio',
                    value: bio,
                    child: Text(
                      bio,
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        fontSize: 14,
                        color: AppColors.onSurfaceVariant,
                        height: 1.4,
                      ),
                    ),
                  ),
                ],
              ],
            ),
          ),

          const SizedBox(height: 24),

          // Stats cards
          Row(
            children: [
              Expanded(child: _buildStatCard('REPORTS SUBMITTED', '$reportsSubmitted', null)),
              const SizedBox(width: 12),
              Expanded(child: _buildStatCard('ROUTES PLANNED', '$routesPlanned', null)),
            ],
          ),

          const SizedBox(height: 28),

          // Manage Account
          Text(
            'Manage Account',
            style: TextStyle(
              fontFamily: 'Plus Jakarta Sans',
              fontWeight: FontWeight.w700,
              fontSize: 18,
              color: AppColors.onSurface,
            ),
          ),

          const SizedBox(height: 14),

          Row(
            children: [
              Expanded(child: _buildAccountAction(Icons.edit_outlined, 'EDIT\nPROFILE', _enterEditMode)),
              const SizedBox(width: 12),
              Expanded(child: _buildAccountAction(Icons.settings_outlined, 'EDIT\nPREFERENCES', () {})),
            ],
          ),

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
                .take(5)
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

  Widget _buildStatCard(String label, String value, String? suffix) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 20, horizontal: 16),
      decoration: BoxDecoration(
        color: AppColors.cardSurface,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(
            color: AppColors.shadow,
            blurRadius: 10,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        children: [
          Text(
            label,
            style: TextStyle(
              fontSize: 10,
              fontWeight: FontWeight.w700,
              letterSpacing: 1.2,
              color: AppColors.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: 8),
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text(
                value,
                style: TextStyle(
                  fontFamily: 'Plus Jakarta Sans',
                  fontWeight: FontWeight.w800,
                  fontSize: 28,
                  color: AppColors.onSurface,
                ),
              ),
              if (suffix != null) ...[
                const SizedBox(width: 4),
                Padding(
                  padding: const EdgeInsets.only(bottom: 4),
                  child: Text(
                    suffix,
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w600,
                      color: AppColors.onSurfaceVariant,
                    ),
                  ),
                ),
              ],
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildAccountAction(IconData icon, String label, VoidCallback onTap) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 20),
        decoration: BoxDecoration(
          color: AppColors.cardSurface,
          borderRadius: BorderRadius.circular(16),
          boxShadow: [
            BoxShadow(
              color: AppColors.shadow,
              blurRadius: 10,
              offset: const Offset(0, 2),
            ),
          ],
        ),
        child: Column(
          children: [
            Icon(icon, color: AppColors.primary, size: 26),
            const SizedBox(height: 10),
            Text(
              label,
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 11,
                fontWeight: FontWeight.w700,
                letterSpacing: 0.8,
                color: AppColors.onSurfaceVariant,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildReportItem(ReportModel report) {
    final verified = report.status == ReportStatus.verified;
    final desc = report.description.length > 40
        ? '${report.description.substring(0, 40)}…'
        : report.description;
    return GestureDetector(
      onTap: () => Navigator.push(
        context,
        MaterialPageRoute(builder: (_) => ReportDetailScreen(report: report)),
      ),
      child: Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      decoration: BoxDecoration(
        color: AppColors.cardSurface,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(
            color: AppColors.shadow,
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Row(
        children: [
          Container(
            width: 42,
            height: 42,
            decoration: BoxDecoration(
              color: AppColors.primary.withOpacity(0.1),
              shape: BoxShape.circle,
            ),
            child: Icon(report.displayIcon, color: AppColors.primary, size: 20),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  report.headline,
                  style: TextStyle(
                    fontWeight: FontWeight.w700,
                    fontSize: 14,
                    color: AppColors.onSurface,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  desc,
                  style: TextStyle(
                    fontSize: 12,
                    color: AppColors.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
          Row(
            children: [
              Container(
                width: 8,
                height: 8,
                decoration: BoxDecoration(
                  color: verified ? AppColors.success : AppColors.warningSoft,
                  shape: BoxShape.circle,
                ),
              ),
              const SizedBox(width: 6),
              Text(
                report.status.label.toUpperCase(),
                style: TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 0.6,
                  color: verified ? AppColors.success : AppColors.warningSoft,
                ),
              ),
            ],
          ),
        ],
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