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

## 中身

| 場所 | 中身 |
|---|---|
| `app/` | 被害者アプリ |
| `attacker/` | 攻撃者アプリ（別の UID・別の署名鍵） |
| `docs/` | 教材本体。上記の URL で配信している中身そのもの |
| `tools/mockserver/` | 第3章で使うモックサーバと教材用の証明書 |
| `tools/frida/` | 観測用のスクリプト |
| `tools/verify/` | 前提が成立しているかを確かめるスクリプト |

## 必要なもの

JDK 17、Android SDK（platform-tools / emulator / system image android-33）、Python 3。
ディスクは 12GB 以上の空きが要ります。詳しくは[環境構築](https://inlet-back.github.io/insecure-app-platform/setup.html)。
