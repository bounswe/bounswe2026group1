/// Minimal JSON matching backend report shape for HTTP mocks / fixtures.
Map<String, dynamic> minimalReportJson({
  required int id,
  String description = 'Test description',
}) =>
    {
      'reportId': id,
      'userId': 1,
      'latitude': 41.0,
      'longitude': 29.0,
      'description': description,
      'reportType': 'OBSTACLE',
      'environment': 'OUTDOOR',
      'status': 'PENDING',
      'agrees': 0,
      'disagrees': 0,
      'publishDate': '2026-05-01T12:00:00Z',
      'mediaUrls': <String>[],
      'objects': [
        {
          'objectType': 'RAMP',
          'issues': <String>['MISSING'],
        },
      ],
    };

Map<String, dynamic> feedPageJson({
  required List<Map<String, dynamic>> content,
  required bool last,
  int number = 0,
  int size = 20,
}) =>
    {
      'content': content,
      'number': number,
      'size': size,
      'last': last,
      'totalPages': last ? number + 1 : number + 2,
    };
