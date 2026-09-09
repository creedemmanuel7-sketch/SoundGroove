import 'package:flutter_queue/queue_logic.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('stableKeys use id#occurrence', () {
    expect(stableKeys([10, 20, 10, 30]), ['10#0', '20#0', '10#1', '30#0']);
    expect(stableKey(7, 2), '7#2');
  });

  test('remaining duration includes current remainder and upcoming', () {
    expect(remainingDurationMs([60000, 120000, 30000], 0, 20000), 130000);
    expect(remainingDurationMs([60000, 120000, 30000], 2, 20000), 10000);
    expect(remainingDurationMs([], 0, 0), 0);
  });

  test('formatRemaining and label', () {
    expect(formatRemaining(500), '0:00');
    expect(formatRemaining(125000), '2:05');
    expect(formatRemaining(3661000), '1:01:01');
    expect(remainingLabel(125000, 3, 4), contains('2:05'));
    expect(remainingLabel(125000, 3, 4), contains('4 titres'));
    expect(remainingLabel(0, 0, 1), '0:00 · 1 titre');
  });
}
