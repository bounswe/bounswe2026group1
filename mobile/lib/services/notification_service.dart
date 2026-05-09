import 'package:flutter/foundation.dart';

import '../models/notification_model.dart';
import 'auth_service.dart';

/// Caches the authenticated user's notifications and exposes an unread count
/// for the top-bar badge. Refreshes on auth flips so the bell empties on
/// logout and re-fills after login.
class NotificationService extends ChangeNotifier {
  final AuthService _auth;

  List<NotificationModel> _items = const [];
  bool _loading = false;
  String? _error;

  NotificationService(this._auth) {
    _auth.addListener(_onAuthChanged);
    if (_auth.isAuthenticated) refresh();
  }

  @override
  void dispose() {
    _auth.removeListener(_onAuthChanged);
    super.dispose();
  }

  // ── State ──────────────────────────────────────────────────────────────────

  List<NotificationModel> get items => _items;
  bool get isLoading => _loading;
  String? get error => _error;
  int get unreadCount => _items.where((n) => !n.read).length;

  // ── Actions ────────────────────────────────────────────────────────────────

  void _onAuthChanged() {
    if (_auth.isAuthenticated) {
      refresh();
    } else {
      _items = const [];
      _error = null;
      notifyListeners();
    }
  }

  Future<void> refresh() async {
    if (!_auth.isAuthenticated) return;
    _loading = true;
    _error = null;
    notifyListeners();
    try {
      _items = await _auth.api.getNotifications();
    } catch (e) {
      _error = 'Could not load notifications.';
    } finally {
      _loading = false;
      notifyListeners();
    }
  }

  /// Marks [id] as read locally for instant feedback, then asks the backend
  /// to persist the change. Reverts the local flag if the call fails so the
  /// badge count stays truthful.
  Future<void> markRead(int id) async {
    if (!_auth.isAuthenticated) return;
    final originalIndex = _items.indexWhere((n) => n.id == id);
    if (originalIndex == -1) return;
    final original = _items[originalIndex];
    if (original.read) return;
    _items = [
      for (final n in _items)
        if (n.id == id)
          NotificationModel(
            id: n.id,
            recipientId: n.recipientId,
            type: n.type,
            message: n.message,
            relatedEntityId: n.relatedEntityId,
            read: true,
            createdAt: n.createdAt,
          )
        else
          n,
    ];
    notifyListeners();
    try {
      await _auth.api.markNotificationRead(id);
    } catch (_) {
      _items = [
        for (final n in _items) if (n.id == id) original else n,
      ];
      notifyListeners();
    }
  }
}
