# MonoTune

[Monochrome](https://github.com/nohimazin/Monochrome) をバックエンドとして統合することを目指した、Android 向け Material 3 YouTube Music クライアントです。

> [!NOTE]
> MonoTune は [OuterTune](https://github.com/OuterTune/OuterTune) のフォークです。
> YouTube Music の検索・プレイリスト・いいね・履歴などの機能を維持しながら、
> 再生バックエンドを Monochrome へ移行するための作業を進めています。

[![License](https://img.shields.io/github/license/nohimazin/MonoTune)](https://www.gnu.org/licenses/gpl-3.0)
![バージョン](https://img.shields.io/badge/version-0.10.2--b1-blue)
![最小 SDK](https://img.shields.io/badge/Android-7.0%2B-green)

---

## 機能 (Features)

### YouTube Music
- 広告なし・バックグラウンド再生
- 楽曲・アルバム・アーティスト・プレイリスト検索
- いいねした曲、再生履歴の閲覧
- アカウント同期（ライブラリ、いいね、再生履歴）

### 再生・音楽体験
- 複数キュー管理（マルチキュー）
- 同期歌詞表示（LRC / TTML / カラオケ形式 対応）
- 音量ノーマライゼーション、テンポ・ピッチ調整
- スリープタイマー
- Android Auto 対応

### Monochrome 統合（開発中）
- Monochrome バックエンドへのトラックマッチング（DB 層実装済み）
- スクロブルキュー（YTM + Monochrome）
- ストリーミング接続は API 仕様確定後に実装予定

### UI / UX
- Material 3 デザイン
- ダークテーマ / ライトテーマ対応

> [!NOTE]
> **ローカルファイル再生は MonoTune の目標ではありません。**
> OuterTune から引き継いだローカルスキャナーのコードは現在も残っていますが、
> 段階的に削除する予定です（詳細は [BRANCHES.md](./BRANCHES.md) を参照）。

> [!WARNING]
> YouTube Music が利用できない地域では、VPN またはプロキシを使用しない限り
> 本アプリは正常に動作しません。

---

## スクリーンショット (Screenshots)

| ホーム | プレイヤー | 歌詞 | ライブラリ |
|--------|-----------|------|------------|
| ![ホーム](assets/gallery/homepage.png) | ![プレイヤー](assets/gallery/player.png) | ![歌詞](assets/gallery/lyrics.png) | ![ライブラリ](assets/gallery/library.png) |

---

## 技術スタック (Tech Stack)

| カテゴリ | 技術 |
|----------|------|
| 言語 | Kotlin |
| UI フレームワーク | Jetpack Compose + Material 3 |
| 再生エンジン | Media3 (ExoPlayer) ※カスタムビルド |
| データベース | Room (SQLite) |
| 依存性注入 | Hilt |
| 非同期処理 | Kotlin Coroutines / Flow |
| ネットワーク | Ktor + OkHttp |
| 画像読み込み | Coil |
| YouTube API | InnerTube プロトコル（独自実装）+ NewPipe Extractor |
| 歌詞プロバイダ | YouTube / LRCLib / KuGou |
| ビルド | Gradle (Kotlin DSL)、Java 21 |

---

## セットアップ / ビルド方法 (Setup)

### 必要環境

- Android Studio (最新安定版推奨)
- JDK 21
- Android SDK (compileSdk 36)

### クローン

サブモジュール（`media/`、`taglib`、`ffMetadataEx` 等）を含めて取得してください。

```bash
git clone --recurse-submodules https://github.com/nohimazin/MonoTune.git
```

サブモジュールが取得できていない場合:

```bash
git submodule update --init --recursive
```

### ビルドフレーバー

| フレーバー | FFmpeg デコーダ | FFmpeg メタデータ抽出 | バージョン更新チェッカー |
|------------|:--------------:|:--------------------:|:----------------------:|
| `core`     | ❌              | ❌                    | ❌                      |
| `full`     | ✅              | ✅                    | ✅                      |

通常は **`coreDebug`** または **`coreRelease`** でビルドするのが最も簡単です。
`full` フレーバーを使う場合は [ffMetadataEx のビルド手順](https://github.com/OuterTune/ffMetadataEx/blob/main/README.md#building) を参照してください。

### Android Studio でのビルド

1. Android Studio でプロジェクトを開く
2. Gradle の同期が完了するのを待つ
3. `Build > Select Build Variant` でフレーバーを選択（例: `coreDebug`）
4. デバイスまたはエミュレーターを選択して実行

### コマンドラインでのビルド

```bash
./gradlew assembleCoreDebug
```

---

## 開発メモ (Dev Notes)

### アーキテクチャ概要

```
app/
├── db/              # Room データベース（エンティティ・DAO）
├── di/              # Hilt モジュール
├── lyrics/          # 歌詞プロバイダ（YouTube / LRCLib / KuGou）
├── models/          # メディアメタデータモデル
├── monochrome/      # Monochrome クライアント（現在スタブ）
├── playback/        # 再生エンジン、キュー、ダウンロード管理
├── ui/              # Compose UI（スクリーン・コンポーネント）
├── utils/           # ユーティリティ（一部は削除予定）
└── viewmodels/      # ViewModel 群

innertube/           # YouTube InnerTube API クライアント
kugou/               # KuGou 歌詞プロバイダモジュール
lrclib/              # LRCLib 歌詞プロバイダモジュール
media/               # Media3 カスタムビルド（git サブモジュール）
```

### コントリビュート

詳細なコントリビュートガイドライン、コミット規約、ブランチ戦略については以下を参照してください。

- [CONTRIBUTING.md](./CONTRIBUTING.md) — ビルド手順・PR ルール・コミットタグ一覧
- [BRANCHES.md](./BRANCHES.md) — ブランチ構成・タグ命名規則・移行ステータス

---

## ロードマップ (Roadmap)

| 項目 | ステータス |
|------|-----------|
| アプリリブランド（MonoTune 名称・パッケージ） | ✅ 完了 |
| YouTube Music 検索・プレイリスト・いいね・履歴 | ✅ 完了 |
| Monochrome 統合レイヤー（DB エンティティ・スタブクライアント） | ✅ 完了 |
| ローカルスキャナーの UI デフォルト無効化 | ✅ 完了 |
| ローカルスキャナーのコード削除 | 🔲 作業中 |
| Monochrome API 実接続（ストリーミング・認証） | 🔲 バックエンド仕様確定待ち |
| `isLocal` / `localPath` DB フィールドの削除 | 🔲 保留 |

---

## クレジット / 帰属 (Attribution)

- [OuterTune](https://github.com/OuterTune/OuterTune) (DD3Boh & contributors) — フォーク元
- [z-huang/InnerTune](https://github.com/z-huang/InnerTune) — オリジナルの基盤
- [Gramophone](https://github.com/FoedusProgramme/Gramophone) — 歌詞パーサー
- [material-color-utilities](https://github.com/material-foundation/material-color-utilities) — Material カラーユーティリティ

---

## 免責事項 (Disclaimer)

このプロジェクトおよびその内容は、YouTube、Google LLC またはその関連会社・子会社とは一切関係がなく、
資金提供、認可、承認を受けているものでもありません。

本プロジェクトで使用されている商標、サービスマーク、商号、その他の知的財産権は、
それぞれの権利者に帰属します。
