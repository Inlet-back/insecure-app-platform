# InsecureAppPlatform

Android の防御境界を、手を動かして確かめる教材です。
意図的に脆弱に作ったアプリと、それを攻める側のアプリの2つを、自分のエミュレータに入れて動かします。

教材本体（読み物と手順）はこちら:
**https://inlet-back.github.io/insecure-app-platform/**

環境構築は上から順に貼っていけば終わります → [環境構築](https://inlet-back.github.io/insecure-app-platform/setup.html)

---

## ⚠ 実機には入れないこと

このリポジトリのアプリは**わざと脆弱に作ってあります**。

- **エミュレータ専用**です。普段使いの端末には入れないでください
- 同梱の**署名鍵（`keys/*.p12`）と証明書・秘密鍵（`tools/mockserver/*.pem`）は使い捨ての教材用**で、誰でも読める前提の材料です。この教材のループバック以外には絶対に使わないでください
- 通信先は**エミュレータから見たホストのループバック（`10.0.2.2`）だけ**です。外部のサービスへは接続しません
- 攻撃の練習は、自分の手元のエミュレータの中だけで行ってください

## 落とす

3つクリックしてください。

- [ソース一式（.zip）](https://github.com/Inlet-back/insecure-app-platform/releases/latest/download/InsecureAppPlatform-src.zip) — 第3章で書き換えて再ビルドします。署名鍵も入っています
- [被害者アプリ（.apk）](https://github.com/Inlet-back/insecure-app-platform/releases/latest/download/app-debug.apk)
- [攻撃者アプリ（.apk）](https://github.com/Inlet-back/insecure-app-platform/releases/latest/download/attacker-debug.apk)

落としたら[環境構築](https://inlet-back.github.io/insecure-app-platform/setup.html)を上から順に貼っていけば、実験できる状態になります。
ダウンロードもコマンドで済ませたい場合は、[手順1](https://inlet-back.github.io/insecure-app-platform/setup.html#s1)にコマンドがあります。

GitHub が自動で付ける **Source code (zip)** と `git clone` は使わないでください。
どちらにも署名鍵が入っておらず、配った APK を上書きインストールできません
（`INSTALL_FAILED_UPDATE_INCOMPATIBLE` で止まります）。

## Extra: Squeeze

Core（第1〜4章）を終えた人向けの、任意の演習です。画面の名前が課題になっている教材用アプリではなく、
商品を選んで買い、会員情報を登録し、ギフト残高を持つ1つの EC アプリを相手に、同じ4つの境界を確かめます。

**配布する3点には入っていません。**ソース一式から自分でビルドしてください。

```sh
./gradlew :squeeze:assembleDebug :squeeze-attacker:assembleDebug
```

Core のアプリとは別のパッケージなので、同じエミュレータに4つ並べて入れられます。
Extra をどこまでやっても、Core の進捗と研究の評価には入りません。

手順は [Extra: Squeeze](https://inlet-back.github.io/insecure-app-platform/extras/squeeze/) にあります。

## 研究について

この教材は研究の評価にも使っています。参加は任意で、参加しなくても教材の内容は変わりません。

既定では**どこへも送信しません**。回答も学習の記録もブラウザの中（localStorage）に留まります。
送信先とその条件は `docs/content/study-config.json` に書いてあり、
いまは収集が無効（`collection_enabled: false`・送信先が空）です。

## 中身

| 場所 | 中身 |
|---|---|
| `app/` | 被害者アプリ |
| `attacker/` | 攻撃者アプリ（別の UID・別の署名鍵） |
| `squeeze/` | Extra用のSqueeze ECアプリ（Coreと別パッケージ） |
| `squeeze-attacker/` | Squeeze専用の境界観測アプリ |
| `docs/` | 教材本体。上記の URL で配信している中身そのもの |
| `tools/mockserver/` | 第3章で使うモックサーバと教材用の証明書 |
| `tools/frida/` | 観測用のスクリプト |
| `tools/verify/` | 前提が成立しているかを確かめるスクリプト |

## 必要なもの

JDK 17、Android SDK（platform-tools / emulator / system image android-33）、Python 3。
ディスクは 12GB 以上の空きが要ります。詳しくは[環境構築](https://inlet-back.github.io/insecure-app-platform/setup.html)。

## ライセンス

[MIT](LICENSE)。教材として自由に使い、改変し、配り直せます。
ただし同梱の署名鍵と証明書・秘密鍵は使い捨ての教材用です。この教材のループバック以外には使わないでください。
