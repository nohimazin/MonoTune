# プロジェクト状況概要 (MonoTune)

更新日: 2026-04-15

## 要件定義 (Requirement Definition)

### 1. Monochrome (TIDAL) 優先統合
- 再生エンジン: YouTube Music (InnerTube) から Monochrome (TIDAL) へ主軸を移行。
- 機能制限: YouTube はライブラリ同期・検索・メタデータ補完に限定し、ストリーミング再生は行わない。

### 2. 高度な音質管理
- ストリーミング品質: AUTO, LOW (96k), HIGH (320k), LOSSLESS (FLAC), HI-RES (24-bit FLAC) を選択可能。
- 個別設定: ストリーミング用とダウンロード用で独立した音質設定を保持。

### 3. 音声トランスコーディング
- 容量軽量化: ダウンロード後に AAC/Opus/MP3 へ自動変換し、元ファイルを整理可能。
- ビットレート指定: 64k から 320k の範囲で手動指定可能。
- 一括変換: 既存ダウンロード楽曲をバックグラウンドでまとめて変換可能。

### 4. CI/CD パイプライン
- マルチバリアント: `core` と `full` の両方を継続検証。
- ビルド安定化: 依存解決、ライセンス検査、native ライブラリ統合の再発防止を重視。

---

## 現在の進捗状況 (Current Progress)

### 完了済み
- [x] Monochrome 再生エンジン統合 (関連サービスとダウンロード処理の更新)。
- [x] ダウンロード音質の独立設定 (設定 UI とキー追加)。
- [x] 音声変換エンジン実装 (FFmpeg-kit ベースの変換処理)。
- [x] ダウンロード後の自動トランスコード統合。
- [x] トランスコード後ファイルのメタデータ継承検証の自動化。
- [x] 一括変換 UI と進捗表示の追加。
- [x] LyricsPlus 公開 API 優先化の実装と関連ビルド修正。
- [x] AboutLibraries の strict license 検査対応。
	- `GNU LESSER GENERAL PUBLIC LICENSE Version 3` を許可リストに追加済み。
- [x] `full` ビルドの native ライブラリ競合解消。
	- `libavcodec.so` などの重複を app 側 packaging (`jniLibs.pickFirsts`) で解消済み。
- [x] ローカル検証結果。
	- `:app:assembleFullDebug` 成功。
	- `--warning-mode all` でも致命的な警告なし。
- [x] 音質選択時の再生信頼性向上（フォールバックチェーン導入）。
	- HI_RES_LOSSLESS → LOSSLESS → HIGH → LOW 自動降級。
	- 再生・ダウンロード処理で一元化された品質トークン機能（MonochromeQualityTokens.kt）。
- [x] プレイヤー詳細表示の「unknown」フォーマット情報補完。
	- PlayerConnection で audioFormat を StateFlow 化し、実行時フォーマット監視。
	- PlayerMenu・DetailsDialog で DB フォーマット不在時に実行時フォーマットにフォールバック。
- [x] Monochrome JIT マッチング精度向上（非ラテン言語対応）。
	- Unicode 対応正規化（`[\p{L}\p{N}\s]`）で日本語等の言語に対応。
	- 2-pass duration tolerance (5s strict + 20s relaxed) で緩和マッチング実装。
- [x] ExoPlayer 互換性修正（DASH マニフェスト対応）。
	- MonochromeClient で DASH/unknown manifest を Error 返却に変更。
	- Progressive media source への unsupported format 渡却を防止。

### 進行中
- [ ] 変換ジョブ失敗時のリトライと中断復旧の最終確認。
- [ ] LyricsPlus パーサーの実運用確認とフォールバック動作の継続監視。

---

## 直近で解消した課題

### 1. ライセンスゲート起因の `full` ビルド失敗
- 事象: `:app:prepareLibraryDefinitionsFullDebug` が not allowed licenses 検出で失敗。
- 対応: AboutLibraries の許可ライセンスへ LGPLv3 を追加。
- 状態: 解消済み。

### 2. LyricsPlus パーサー起因の unit test 失敗
- 事象: `:app:testCoreDebugUnitTest` の `LyricsPlusPublicLyricsProviderTest` で nested line array の検証が失敗。
- 対応: 配列抽出を単一 `text` 抽出より優先するように修正。
- 状態: 解消済み。

### 3. Native `.so` 重複起因の `mergeFullDebugNativeLibs` 失敗
- 事象: `ffMetadataEx` と `ffmpeg-kit` の両方から `libav*.so` が流入し競合。
- 対応: app 側で `jniLibs.pickFirsts` を設定し、同名ライブラリの統合ルールを明示。
- 状態: 解消済み。

### 4. プレイヤー詳細表示が常に「unknown」フォーマット表示
- 事象: PlayerMenu の DetailsDialog で format 情報が "unknown" と表示される。
- 原因: DB フォーマット情報が全曲に保存されていない（YTM 曲は runtime のみ）。
- 対応:
  - PlayerConnection に `currentAudioFormat: MutableStateFlow<Format?>` を追加し listener で更新。
  - Dialog.kt で `(currentFormat?.mimeType ?: playerAudioFormat?.sampleMimeType)` フォールバック追加。
- 관련파일: [PlayerConnection.kt](app/src/main/kotlin/com/nohimazin/monotune/playback/PlayerConnection.kt), [Dialog.kt](app/src/main/kotlin/com/nohimazin/monotune/ui/screens/player/Dialog.kt), [PlayerMenu.kt](app/src/main/kotlin/com/nohimazin/monotune/ui/screens/player/PlayerMenu.kt)
- 状態: 解消済み。

### 5. Hi-Res (24-bit FLAC) 再生失敗
- 事象: HI_RES_LOSSLESS 選択時に curl または "unsupported stream" エラーで再生失敗。
- 原因（複数）:
  1. Monochrome JIT マッチングで非ラテン文字（日本語等）の曲が TIDAL 側で見つからない。
  2. HI-Res 配信がない場合、フォールバック機構がなく即エラー。
  3. DASH manifest 返却時に base64 data URI でラップされ、ExoPlayer の progressive source で処理不可。
- 対応:
  1. MonochromeSearchMatcher で Unicode 対応正規化を実装、2-pass duration matching (5s strict → 20s relaxed)。
  2. MusicService・DownloadUtil に quality fallback loop を導入。
  3. MonochromeQualityTokens.kt を新規作成し、品質トークン列を一元化。
  4. MonochromeClient の resolveStreamUrl() で DASH/unknown manifest を Error 返却に変更。
- 関連ファイル: [MonochromeSearchMatcher.kt](app/src/main/kotlin/com/nohimazin/monotune/service/Monochrome/MonochromeSearchMatcher.kt), [MusicService.kt](app/src/main/kotlin/com/nohimazin/monotune/playback/MusicService.kt), [DownloadUtil.kt](app/src/main/kotlin/com/nohimazin/monotune/download/DownloadUtil.kt), [MonochromeQualityTokens.kt](app/src/main/kotlin/com/nohimazin/monotune/service/Monochrome/MonochromeQualityTokens.kt), [MonochromeClient.kt](app/src/main/kotlin/com/nohimazin/monotune/service/Monochrome/MonochromeClient.kt)
- 状態: 解消済み。

### 6. Copilot PR #36 レビューフィードバック対応
- 事象: PR に Copilot から複数コメント（インデント、KDoc、一貫性）。
- 対応:
  - MonochromeSearchMatcher.kt のインデント修正。
  - RELAXED_DURATION_TOLERANCE_SECS の 2-pass matching を KDoc に明示。
  - 品質トークン定義を MonochromeQualityTokens.kt に一元化し、MusicService・DownloadUtil で共用。
- 状態: 解消済み。

---

## 発見された保留課題（コード走査検証結果）

### 1. 高優先度課題 🔴

#### 1.1 DownloadUtil.kt - フォールバックループの例外ハンドリング不足
- **問題**: `ResolvingDataSource.Factory` 内のフォールバックループで IOException、JSONException、socket timeout などが完全に未処理。
- **影響**: ダウンロード失敗時に適切なエラー処理ができず、ユーザーへのフィードバック不足。
- **対応**: ループ内に try-catch を追加し、例外ごとにログを記録＆フォールバック重試行。
- **関連ファイル**: [DownloadUtil.kt](app/src/main/kotlin/com/nohimazin/monotune/download/DownloadUtil.kt#L144-L172)
- **状態**: 未対応

#### 1.2 MusicService.kt - Coroutine scope 管理と runBlocking() 濫用
- **問題**: 
  - `playQueue()` で新規の `CoroutineScope(Dispatchers.Main)` を生成し、cleanup が不確実。
  - 複数の `runBlocking {}` で Main thread をブロック（ANR リスク）。
  - MusicService 破棄時に起動した coroutine が cleanup されない。
- **影響**: UI フリーズ、メモリリーク、ANR （Application Not Responding）エラー。
- **対応**: `scope` (viewmodel scope or service scope) に統一し、lifecycle-aware cleanup を実装。`runBlocking` を async/await で置換。
- **関連ファイル**: [MusicService.kt](app/src/main/kotlin/com/nohimazin/monotune/playback/MusicService.kt#L750-L791)
- **状態**: 未対応

### 2. 中優先度課題 🟡

#### 2.1 PlayerConnection.kt - StateFlow 初期化タイミング問題
- **問題**: `player.addListener()` 登録時に、既に設定されている `player.audioFormat` が同期されない。リスナー登録前の値が失われる。
- **影響**: プレイヤー起動直後にフォーマット情報が正しく反映されない場合がある。
- **対応**: `init {}` ブロック内で `player.addListener()` の直後に `currentAudioFormat.value = player.audioFormat` を明示的に設定。
- **関連ファイル**: [PlayerConnection.kt](app/src/main/kotlin/com/nohimazin/monotune/playback/PlayerConnection.kt#L65-L183)
- **状態**: 未対応

#### 2.2 MonochromeSearchMatcher.kt - キャッシュ戦略の原始性（全キャッシュクリア）
- **問題**: `MAX_CACHE_ENTRIES = 200` で満杯時に全キャッシュをクリアしている（全削除戦略）。メモリ効率が低く、キャッシュ率の低下につながる。
- **影響**: 同じ曲の JIT マッチングが頻繁に再実行され、API リクエスト数増加とレイテンシ悪化。
- **対応**: LRU（Least Recently Used）エビクション戦略を実装（例：LinkedHashMap or Google's EvictingQueue）。
- **関連ファイル**: [MonochromeSearchMatcher.kt](app/src/main/kotlin/com/nohimazin/monotune/service/Monochrome/MonochromeSearchMatcher.kt#L44-L55)
- **状態**: 未対応

#### 2.3 MonochromeSearchMatcher.kt - Unicode 正規化の完全性不足
- **問題**: `text.lowercase()` + `\p{L}` は言語固有の正規化（Turkishの İ/i など）や結合文字を処理していない。
- **影響**: 特殊言語や結合文字を含む曲名で マッチング精度が低下する可能性。
- **対応**: Java の `Normalizer.normalize(text, Form.NFKD)` を使用し、Unicode正規化を仕上げる。
  ```kotlin
  private fun normalize(text: String): String =
      Normalizer.normalize(text, Normalizer.Form.NFKD)
          .lowercase(Locale.ENGLISH)
          .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
          .replace(Regex("\\s+"), " ")
          .trim()
  ```
- **関連ファイル**: [MonochromeSearchMatcher.kt](app/src/main/kotlin/com/nohimazin/monotune/service/Monochrome/MonochromeSearchMatcher.kt#L152-L158)
- **状態**: 未対応

#### 2.4 MusicService.kt - recoverSong() のネットワーク例外処理不足
- **問題**: `YTPlayerUtils.playerResponseForMetadata()` などが NetworkException を投げた場合、捕捉されない。
- **影響**: ネットワーク障害時に曲復旧ロジックが中断する。
- **対応**: try-catch で NetworkException をキャッチし、リトライロジックまたはフォールバックを実装。
- **関連ファイル**: [MusicService.kt](app/src/main/kotlin/com/nohimazin/monotune/playback/MusicService.kt#L400-L430)
- **状態**: 未対応

### 3. 低優先度課題 🟢

#### 3.1 PlayerConnection.kt - StateFlow SharingStarted 戦略の見直し
- **問題**: `isPlaying` で `SharingStarted.Lazily` を使用しているため、最初の subscriber まで emit が遅延される。
- **影響**: UI アタッチ前に初期値がセットされない可能性（見落としにくい）。
- **対応**: `SharingStarted.Eagerly` への変更検討（ただしメモリとのトレードオフ）。
- **関連ファイル**: [PlayerConnection.kt](app/src/main/kotlin/com/nohimazin/monotune/playback/PlayerConnection.kt#L67)
- **状態**: 検討中

#### 3.2 PlayerMenu.kt・Dialog.kt - Recomposition 最適化
- **問題**: 複数の `collectAsState()` が呼ばれており、各変更時に PlayerMenu 全体が recomposition される。特に `currentAudioFormat` が頻繁に変わる場合、UI パフォーマンス低下。
- **影響**: 再生中の frame drop や UI 反応遅延。
- **対応**: Flow.combine() で複数 state をマージし、バッチ購読を実装。Composition の構造を細粒化。
- **関連ファイル**: [PlayerMenu.kt](app/src/main/kotlin/com/nohimazin/monotune/ui/screens/player/PlayerMenu.kt#L139-L150)
- **状態**: 検討中

### 4. コード品質総合スコア

| 領域 | スコア | 状態 |
|------|--------|------|
| エラーハンドリング | 68% | ⚠️ DownloadUtil・MusicService で漏れあり |
| リソース管理 | 64% | ⚠️ coroutine scope cleanup・キャッシュ戦略で改善必要 |
| Null Safety・型安全性 | 85% | ✅ Kotlin null safety に準拠 |
| ログ出力 | 72% | ⚠️ 冗長なログが複数箇所 |
| UI パフォーマンス | 70% | ⚠️ recomposition の最適化余地あり |

---

## 発見された課題への対応計画 (Priority-based Implementation)

### Phase 1: 高優先度リスク排除 (即対応)

#### Week 1-2: DownloadUtil.kt 例外処理強化
- `ResolvingDataSource.Factory` 内のフォールバックループに try-catch を追加
- ネットワーク例外、JSON 解析例外を個別に処理
- エラーログを構造化し、ユーザー向けフィードバック改善
- テスト: ネットワーク遮断時、不正レスポンス時の挙動検証

#### Week 2-3: MusicService.kt Coroutine scope 管理
- `playQueue()` で新規 scope 生成を廃止し、サービス scope に統一
- `runBlocking` を suspend/async/await に置換（または改善戦略を検討）
- cancellation token / job tracking を実装
- ANR 回避のため Main thread block を完全排除

### Phase 2: 中程度安定性向上 (次月)

#### Week 4-5: PlayerConnection 初期化修正
- `init {}` で `currentAudioFormat.value = player.audioFormat` を明示設定
- リスナー登録前の値損失を防止
- UI テスト: 起動直後のフォーマット表示確認

#### Week 5-6: MonochromeSearchMatcher LRU キャッシュ導入
- LinkedHashMap or Apache Commons EvictingQueue へ移行
- キャッシュ効率指標を計測・ログ
- API リクエスト削減の効果を測定

#### Week 6-7: Unicode 正規化完全化
- `java.text.Normalizer` 導入して NFKD 正規化
- 言語別テストケース追加（Turkish, 日本語等）
- マッチング精度の向上を検証

### Phase 3: 生産性・パフォーマンス改善 (来月以降)

#### Week 8+: MusicService.kt ネットワーク例外処理
- NetworkException キャッチと自動リトライロジック
- exponential backoff 実装

#### Week 9+: PlayerMenu recomposition 最適化
- Flow.combine() で複数 state をバッチ購読
- Composition 細粒化による frame drop 削減

---

## 次の実装計画 (Next Steps)

### 1. トランスコード品質検証の自動化
- 変換前後でタグ/アートワーク/歌詞が保持されるかを比較する検証フローを追加。
- 対象フォーマット: FLAC -> AAC, FLAC -> Opus, AAC -> MP3。

### 2. UI/UX 改善
- ライブラリ一覧に保存フォーマット/ビットレート表示を追加。
- 一括変換中の Foreground Notification とキャンセル導線を改善。

### 3. パフォーマンスと安定性
- 長時間変換時の負荷制御 (省電力モード時の同時ジョブ数制限) を導入。
- 途中終了時のテンポラリ掃除とアトミック置換の再検証を実施。

---

## 懸念事項・監視ポイント

- Android 13+ のメディア権限周辺で、変換後置換時の権限維持確認を継続。
- 長時間トランスコード時の発熱/電池消費は引き続き監視。
- 外部依存 (ffmpeg fork, AboutLibraries) の更新でライセンス表記や ABI 構成が変わる可能性に注意。
- LyricsPlus 公開 API の応答形式差異が残る可能性があるため、例外時のフォールバック経路は継続監視。
- トランスコードの検証は標準タグと埋め込み画像を中心に自動化済みで、歌詞タグは実装依存のため継続観測。
