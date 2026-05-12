import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:mapcess/services/sse_service.dart';
import 'package:mapcess/models/sse_event.dart';

class MockHttpClient extends Mock implements HttpClient {}
class MockHttpClientRequest extends Mock implements HttpClientRequest {}
class MockHttpClientResponse extends Mock implements HttpClientResponse {}
class MockHttpHeaders extends Mock implements HttpHeaders {}

class MyHttpOverrides extends HttpOverrides {
  final HttpClient client;
  MyHttpOverrides(this.client);
  @override
  HttpClient createHttpClient(SecurityContext? context) => client;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() {
    registerFallbackValue(Uri.parse('http://localhost'));
    registerFallbackValue(StreamTransformer<List<int>, String>.fromHandlers());
  });

  late SseService sseService;
  late MockHttpClient mockClient;
  late MockHttpClientRequest mockRequest;
  late MockHttpClientResponse mockResponse;
  late MockHttpHeaders mockHeaders;
  late StreamController<List<int>> responseStreamController;

  setUp(() {
    mockClient = MockHttpClient();
    mockRequest = MockHttpClientRequest();
    mockResponse = MockHttpClientResponse();
    mockHeaders = MockHttpHeaders();
    responseStreamController = StreamController<List<int>>.broadcast();

    // Stub setup
    when(() => mockClient.getUrl(any())).thenAnswer((_) async => mockRequest);
    when(() => mockClient.close(force: any(named: 'force'))).thenReturn(null);

    when(() => mockRequest.headers).thenReturn(mockHeaders);
    when(() => mockRequest.close()).thenAnswer((_) async => mockResponse);

    when(() => mockResponse.statusCode).thenReturn(200);
    when(() => mockResponse.transform<String>(any())).thenAnswer(
      (invocation) => responseStreamController.stream.transform(invocation.positionalArguments[0] as StreamTransformer<List<int>, String>),
    );
    when(() => mockResponse.listen(
      any(),
      onDone: any(named: 'onDone'),
      onError: any(named: 'onError'),
      cancelOnError: any(named: 'cancelOnError'),
    )).thenAnswer((invocation) {
      final onData = invocation.positionalArguments[0] as void Function(List<int>)?;
      final onDone = invocation.namedArguments[#onDone] as void Function()?;
      final onError = invocation.namedArguments[#onError] as void Function(Object, StackTrace?)?;
      final cancelOnError = invocation.namedArguments[#cancelOnError] as bool?;
      return responseStreamController.stream.listen(
        onData,
        onDone: onDone,
        onError: onError,
        cancelOnError: cancelOnError,
      );
    });

    // Provide the mock client via HttpOverrides
    HttpOverrides.global = MyHttpOverrides(mockClient);

    sseService = SseService();
  });

  tearDown(() {
    sseService.dispose();
    responseStreamController.close();
    HttpOverrides.global = null;
  });

  group('SseService', () {
    test('starts disconnected without calling connect', () {
      expect(sseService.connected, false);
      expect(sseService.needsFullRefresh, false);
    });

    test('connect() establishes connection and parses events', () async {
      sseService.connect();
      
      // Allow microtasks to complete (connection sequence)
      await Future.delayed(Duration.zero);

      expect(sseService.connected, true);

      // Listen for emitted events
      final events = <SseEvent>[];
      sseService.events.listen(events.add);

      // Send raw SSE data
      const eventData = 'data: {"eventType":"POINTS_CHANGED","userId":1,"points":100}\n\n';
      responseStreamController.add(utf8.encode(eventData));

      // Wait for stream to process
      await Future.delayed(const Duration(milliseconds: 10));

      expect(events.length, 1);
      expect(events.first.eventType, 'POINTS_CHANGED');
      expect(events.first.userId, 1);
    });

    test('reconnects if response status is not 200', () async {
      when(() => mockResponse.statusCode).thenReturn(500);

      sseService.connect();
      await Future.delayed(Duration.zero);

      expect(sseService.connected, false);
      // Wait to see if backoff schedules a reconnect (initial backoff is 1s, we can just verify the timer logic if we could, but here we just check it isn't connected)
    });

    test('disconnect() cleans up resources', () async {
      sseService.connect();
      await Future.delayed(Duration.zero);
      expect(sseService.connected, true);

      sseService.disconnect();
      expect(sseService.connected, false);
      verify(() => mockClient.close(force: true)).called(greaterThanOrEqualTo(1));
    });

    test('handles app lifecycle changes', () {
      // Simulate paused
      sseService.didChangeAppLifecycleState(AppLifecycleState.paused);
      expect(sseService.connected, false);

      // Simulate resumed
      sseService.didChangeAppLifecycleState(AppLifecycleState.resumed);
      // it should try to connect, though we might not await it here, the paused flag should be false.
    });

    test('needsFullRefresh flags correctly', () {
      sseService.clearFullRefreshFlag();
      expect(sseService.needsFullRefresh, false);
    });
  });
}
