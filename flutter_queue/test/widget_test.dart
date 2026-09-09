import 'package:flutter/material.dart';
import 'package:flutter_queue/main.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('queue sections render without emoji', (tester) async {
    await tester.pumpWidget(
      QueueApp(
        seed: {
          'remainingLabel': '12:00 restant · 3 titres',
          'isPlaying': true,
          'accent': 0xFFA855F7,
          'history': [
            {'index': 0, 'id': 1, 'key': '1#0', 'title': 'Hier', 'artist': 'A', 'duration': '3:00'},
          ],
          'nowPlaying': {
            'index': 1,
            'id': 2,
            'key': '2#0',
            'title': 'Maintenant',
            'artist': 'B',
            'duration': '4:00',
          },
          'upcoming': [
            {'index': 2, 'id': 3, 'key': '3#0', 'title': 'Ensuite', 'artist': 'C', 'duration': '5:00'},
          ],
        },
      ),
    );
    expect(find.text("File d'attente"), findsOneWidget);
    expect(find.text('En cours'), findsOneWidget);
    expect(find.text('À suivre'), findsOneWidget);
    expect(find.text('Maintenant'), findsOneWidget);
    expect(find.text('Ensuite'), findsOneWidget);
    expect(find.text('Tout effacer'), findsOneWidget);
    expect(find.textContaining(RegExp(r'[\u{1F300}-\u{1FAFF}]', unicode: true)), findsNothing);
  });
}
