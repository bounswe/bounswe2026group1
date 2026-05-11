import 'package:flutter_test/flutter_test.dart';

import 'package:mapcess/services/sse_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('SseService', () {
    test('starts disconnected without calling connect', () {
      final sse = SseService();
      expect(sse.connected, false);
      expect(sse.needsFullRefresh, false);
      sse.clearFullRefreshFlag();
      sse.dispose();
    });
  });
}
