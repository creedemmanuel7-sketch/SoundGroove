const graphiteAbyss = 0xFF0A0A0C;
const graphiteCard = 0xFF17181C;
const brandPurple = 0xFFA855F7;

String stableKey(int songId, int occurrence) => '$songId#$occurrence';

List<String> stableKeys(List<int> ids) {
  final seen = <int, int>{};
  return [
    for (final id in ids)
      stableKey(id, () {
        final n = seen[id] ?? 0;
        seen[id] = n + 1;
        return n;
      }()),
  ];
}

int remainingDurationMs(List<int> durationsMs, int currentIndex, int positionMs) {
  if (durationsMs.isEmpty) return 0;
  final index = currentIndex.clamp(0, durationsMs.length - 1);
  final remainingCurrent =
      (durationsMs[index] - (positionMs < 0 ? 0 : positionMs)).clamp(0, 1 << 62);
  var upcoming = 0;
  for (var i = index + 1; i < durationsMs.length; i++) {
    upcoming += durationsMs[i] < 0 ? 0 : durationsMs[i];
  }
  return remainingCurrent + upcoming;
}

String formatRemaining(int durationMs) {
  if (durationMs < 1000) return '0:00';
  final totalSeconds = durationMs ~/ 1000;
  final hours = totalSeconds ~/ 3600;
  final minutes = (totalSeconds % 3600) ~/ 60;
  final seconds = totalSeconds % 60;
  if (hours > 0) {
    return '$hours:${minutes.toString().padLeft(2, '0')}:${seconds.toString().padLeft(2, '0')}';
  }
  return '$minutes:${seconds.toString().padLeft(2, '0')}';
}

String remainingLabel(int durationMs, int upcomingCount, int totalCount) {
  final time = formatRemaining(durationMs);
  final count = totalCount == 1 ? '1 titre' : '$totalCount titres';
  if (upcomingCount > 0) return '$time restant · $count';
  return '$time · $count';
}
