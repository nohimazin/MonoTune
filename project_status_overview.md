# プロジェクト状況概要 (MonoTune)

更新日: 2026-04-02

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
