import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mapcess/screens/avatar_crop_screen.dart';

/// A 1×1 transparent PNG — small enough to keep in source, real enough for
/// Image.file to decode without painting failures in the widget tree.
const _onePixelPngBytes = <int>[
  0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // PNG signature
  0x00, 0x00, 0x00, 0x0D, // IHDR length
  0x49, 0x48, 0x44, 0x52, // "IHDR"
  0x00, 0x00, 0x00, 0x01, // width = 1
  0x00, 0x00, 0x00, 0x01, // height = 1
  0x08, 0x06, 0x00, 0x00, 0x00, // bit depth 8, RGBA
  0x1F, 0x15, 0xC4, 0x89, // IHDR CRC
  0x00, 0x00, 0x00, 0x0A, // IDAT length
  0x49, 0x44, 0x41, 0x54, // "IDAT"
  0x78, 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00, 0x05, 0x00, 0x01,
  0x0D, 0x0A, 0x2D, 0xB4, // IDAT CRC
  0x00, 0x00, 0x00, 0x00, // IEND length
  0x49, 0x45, 0x4E, 0x44, // "IEND"
  0xAE, 0x42, 0x60, 0x82, // IEND CRC
];

void main() {
  late Directory tmpDir;
  late File pngFile;

  setUpAll(() async {
    tmpDir = await Directory.systemTemp.createTemp('avatar_crop_test_');
    pngFile = File('${tmpDir.path}/sample.png');
    await pngFile.writeAsBytes(Uint8List.fromList(_onePixelPngBytes));
  });

  tearDownAll(() async {
    if (await tmpDir.exists()) {
      await tmpDir.delete(recursive: true);
    }
  });

  group('AvatarCropScreen', () {
    testWidgets('renders title, reset and bottom-bar buttons', (tester) async {
      await tester.pumpWidget(
        MaterialApp(home: AvatarCropScreen(source: pngFile)),
      );
      // The hint card uses a real Image.file decode; one extra pump lets the
      // decode microtask settle without waiting on FlutterMap-style network.
      await tester.pump();

      expect(find.text('Crop avatar'), findsOneWidget);
      expect(find.text('Reset'), findsOneWidget);
      expect(find.text('Cancel'), findsOneWidget);
      expect(find.text('Use photo'), findsOneWidget);
      expect(
        find.text('Drag to reposition · pinch to zoom'),
        findsOneWidget,
      );
    });

    testWidgets('Cancel button pops the screen without a result',
        (tester) async {
      File? result = pngFile;

      await tester.pumpWidget(
        MaterialApp(
          home: Builder(
            builder: (ctx) => Scaffold(
              body: Center(
                child: ElevatedButton(
                  onPressed: () async {
                    result = await Navigator.of(ctx).push<File>(
                      MaterialPageRoute(
                        builder: (_) => AvatarCropScreen(source: pngFile),
                      ),
                    );
                  },
                  child: const Text('open'),
                ),
              ),
            ),
          ),
        ),
      );

      await tester.tap(find.text('open'));
      await tester.pumpAndSettle();
      expect(find.text('Crop avatar'), findsOneWidget);

      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();

      expect(result, isNull);
      expect(find.text('open'), findsOneWidget);
    });

    testWidgets('Reset can be tapped without throwing', (tester) async {
      await tester.pumpWidget(
        MaterialApp(home: AvatarCropScreen(source: pngFile)),
      );
      await tester.pump();

      // Reset only resets the TransformationController matrix — there is no
      // visible state change to assert on, but tapping it must not crash.
      await tester.tap(find.text('Reset'));
      await tester.pump();

      expect(find.text('Crop avatar'), findsOneWidget);
    });
  });
}
