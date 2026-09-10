import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_queue/queue_logic.dart';

const _methodChannel = MethodChannel('com.credo.soundgroove/queue');
const _eventChannel = EventChannel('com.credo.soundgroove/queue_events');

void main() => runApp(const QueueApp());

class QueueApp extends StatelessWidget {
  const QueueApp({super.key, this.seed});

  final Map<String, dynamic>? seed;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        brightness: Brightness.dark,
        scaffoldBackgroundColor: const Color(graphiteAbyss),
        colorScheme: const ColorScheme.dark(primary: Color(brandPurple)),
        useMaterial3: true,
      ),
      home: QueuePage(seed: seed),
    );
  }
}

class QueuePage extends StatefulWidget {
  const QueuePage({super.key, this.seed});

  final Map<String, dynamic>? seed;

  @override
  State<QueuePage> createState() => _QueuePageState();
}

class _QueuePageState extends State<QueuePage> {
  Map<String, dynamic> _snapshot = const {};
  bool _historyExpanded = false;

  @override
  void initState() {
    super.initState();
    if (widget.seed != null) {
      _snapshot = widget.seed!;
    } else {
      _eventChannel.receiveBroadcastStream().listen((event) {
        if (event is String) {
          setState(() => _snapshot = jsonDecode(event) as Map<String, dynamic>);
        }
      });
      _methodChannel.invokeMethod<String>('latest').then((value) {
        if (value != null && mounted) {
          setState(() => _snapshot = jsonDecode(value) as Map<String, dynamic>);
        }
      });
    }
  }

  List<Map<String, dynamic>> _list(String key) {
    final raw = _snapshot[key];
    if (raw is! List) return const [];
    return raw.whereType<Map>().map((e) => Map<String, dynamic>.from(e)).toList();
  }

  @override
  Widget build(BuildContext context) {
    final accent = Color((_snapshot['accent'] as num?)?.toInt() ?? brandPurple);
    final remaining = _snapshot['remainingLabel'] as String? ?? '0:00 · 0 titres';
    final isPlaying = _snapshot['isPlaying'] == true;
    final history = _list('history');
    final upcoming = _list('upcoming');
    final now = _snapshot['nowPlaying'];
    final nowMap = now is Map ? Map<String, dynamic>.from(now) : null;

    return Scaffold(
      backgroundColor: const Color(graphiteAbyss),
      body: SafeArea(
        child: Column(
          children: [
            GestureDetector(
              onTap: () => _methodChannel.invokeMethod('close'),
                    onVerticalDragEnd: (details) {
                if ((details.primaryVelocity ?? 0) > 900) {
                  _methodChannel.invokeMethod('close');
                }
              },
              child: SizedBox(
                height: queueHandleHit,
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Container(
                      width: queueHandleWidth,
                      height: queueHandleHeight,
                      decoration: BoxDecoration(
                        color: Color(queueGlassFill),
                        borderRadius: BorderRadius.circular(999),
                        border: Border.all(color: const Color(queueGlassStroke)),
                      ),
                    ),
                    Icon(Icons.expand_more, color: Colors.white.withValues(alpha: 0.55), size: 22),
                  ],
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 8, 8, 12),
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          "File d'attente",
                          style: TextStyle(
                            color: Colors.white,
                            fontWeight: FontWeight.w700,
                            fontSize: 18,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          remaining,
                          style: TextStyle(
                            color: Colors.white.withValues(alpha: 0.62),
                            fontSize: 12,
                          ),
                        ),
                      ],
                    ),
                  ),
                  SizedBox(
                    height: queueHandleHit,
                    child: TextButton(
                      onPressed: () => _methodChannel.invokeMethod('close'),
                      child: Text('Fermer', style: TextStyle(color: accent, fontWeight: FontWeight.w600)),
                    ),
                  ),
                ],
              ),
            ),
            Expanded(
              child: ListView(
                padding: const EdgeInsets.fromLTRB(20, 4, 20, 40),
                children: [
                  if (history.isNotEmpty)
                    _SectionTap(
                      title: 'Historique',
                      meta: '${history.length}',
                      accent: accent,
                      onTap: () => setState(() => _historyExpanded = !_historyExpanded),
                    ),
                  if (_historyExpanded)
                    ...history.map((row) => _TrackTile(row: row, dimmed: true, accent: accent)),
                  if (nowMap != null) _NowPlayingCard(row: nowMap, isPlaying: isPlaying, accent: accent),
                  _UpcomingHeader(
                    count: upcoming.length,
                    accent: accent,
                    onClear: upcoming.isEmpty
                        ? null
                        : () => _methodChannel.invokeMethod('clearUpcoming'),
                  ),
                  ReorderableListView.builder(
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    itemCount: upcoming.length,
                    onReorder: (from, to) {
                      if (from == to) return;
                      final src = upcoming[from]['index'] as int;
                      final dstIndex = to > from ? to - 1 : to;
                      final dst = upcoming[dstIndex]['index'] as int;
                      _methodChannel.invokeMethod('move', {'from': src, 'to': dst});
                    },
                    itemBuilder: (context, i) {
                      final row = upcoming[i];
                      return Dismissible(
                        key: ValueKey(row['key'] ?? '${row['id']}#$i'),
                        direction: DismissDirection.endToStart,
                        background: Container(
                          alignment: Alignment.centerRight,
                          padding: const EdgeInsets.symmetric(horizontal: 20),
                          color: const Color(0xFFE0554F),
                          child: const Text('Retirer', style: TextStyle(color: Colors.white, fontWeight: FontWeight.w600)),
                        ),
                        onDismissed: (_) =>
                            _methodChannel.invokeMethod('remove', {'index': row['index']}),
                        child: _TrackTile(
                          row: row,
                          accent: accent,
                          showHandle: true,
                          onTap: () => _methodChannel.invokeMethod('play', {'index': row['index']}),
                        ),
                      );
                    },
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _SectionTap extends StatelessWidget {
  const _SectionTap({required this.title, required this.meta, required this.accent, required this.onTap});
  final String title;
  final String meta;
  final Color accent;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      dense: true,
      onTap: onTap,
      title: Text(title, style: TextStyle(color: Colors.white.withValues(alpha: 0.78), fontWeight: FontWeight.w600)),
      trailing: Text(meta, style: TextStyle(color: accent)),
    );
  }
}

class _UpcomingHeader extends StatelessWidget {
  const _UpcomingHeader({required this.count, required this.accent, this.onClear});
  final int count;
  final Color accent;
  final VoidCallback? onClear;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(4, 8, 0, 4),
      child: Row(
        children: [
          Text('À suivre', style: TextStyle(color: Colors.white.withValues(alpha: 0.78), fontWeight: FontWeight.w600)),
          const Spacer(),
          Text('$count', style: TextStyle(color: accent)),
          if (onClear != null)
            TextButton(
              onPressed: onClear,
              child: Text('Tout effacer', style: TextStyle(color: accent, fontWeight: FontWeight.w600)),
            ),
        ],
      ),
    );
  }
}

class _NowPlayingCard extends StatelessWidget {
  const _NowPlayingCard({required this.row, required this.isPlaying, required this.accent});
  final Map<String, dynamic> row;
  final bool isPlaying;
  final Color accent;

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.symmetric(vertical: queueRowGap),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: const Color(queueGlassFill),
        borderRadius: BorderRadius.circular(queueCardRadius),
        border: Border.all(color: accent.withValues(alpha: 0.38)),
      ),
      child: Row(
        children: [
          _Art(accent: accent),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('En cours', style: TextStyle(color: accent, fontSize: 12, fontWeight: FontWeight.w600)),
                Text(
                  row['title']?.toString() ?? '',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w700, fontSize: 16),
                ),
                Text(
                  row['artist']?.toString() ?? '',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(color: Colors.white.withValues(alpha: 0.7)),
                ),
              ],
            ),
          ),
          Icon(isPlaying ? Icons.equalizer : Icons.pause, color: accent),
        ],
      ),
    );
  }
}

class _TrackTile extends StatelessWidget {
  const _TrackTile({
    required this.row,
    required this.accent,
    this.dimmed = false,
    this.showHandle = false,
    this.onTap,
  });

  final Map<String, dynamic> row;
  final Color accent;
  final bool dimmed;
  final bool showHandle;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return Opacity(
      opacity: dimmed ? 0.72 : 1,
      child: ListTile(
        contentPadding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
        onTap: onTap,
        leading: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (showHandle) Icon(Icons.drag_handle, color: Colors.white.withValues(alpha: 0.45)),
            _Art(accent: accent, small: true),
          ],
        ),
        title: Text(
          row['title']?.toString() ?? '',
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600),
        ),
        subtitle: Text(
          row['artist']?.toString() ?? '',
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: TextStyle(color: Colors.white.withValues(alpha: 0.62)),
        ),
        trailing: Text(
          row['duration']?.toString() ?? '',
          style: TextStyle(color: Colors.white.withValues(alpha: 0.5), fontSize: 12),
        ),
      ),
    );
  }
}

class _Art extends StatelessWidget {
  const _Art({required this.accent, this.small = false});
  final Color accent;
  final bool small;

  @override
  Widget build(BuildContext context) {
    final size = small ? 40.0 : 56.0;
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        color: const Color(graphiteCard),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Icon(Icons.music_note, color: accent, size: size * 0.4),
    );
  }
}
