/*
 * 第4章 Deep — Android Keystore の鍵を「取り出さずに使う」。
 *
 *   (アプリの Keystore 画面を «前面に出したまま» 、リポジトリのルートから)
 *   frida -U -F -l tools/frida/ch4-use-keystore-key.js
 *   (先にその画面で一度«暗号化»しておくこと)
 *
 * 何をしているか:
 *   AndroidKeyStore から2本の鍵のハンドルを順に取り、
 *   shared_prefs/tee_demo.xml に保存された暗号文をそれぞれ復号しようとする。
 *     tee_demo_key       認証なしの鍵
 *     tee_demo_key_auth  使用にユーザー認証を要求する鍵
 *   鍵のバイト列は一度も取得していない。というより取得できない。
 *
 * ここで確認すること:
 *   「鍵を抜けない」= Android Keystore が守っている。これは本当。
 *   「復号されない」= 別の話。アプリとして動けば、認証なしの鍵は使える。
 *   認証つきの鍵は Keystore 側が使用を断るので、同じプロセスにいても通らない。
 *
 *   ただし「アプリの外の判定だから絶対に飛ばせない」わけではない。
 *   Keystore が強制するのは «その鍵を使えるかどうか» だけで、
 *   認証が通ったあとの平文や、認証を呼び出すアプリ側の処理は
 *   相変わらずプロセス内の攻撃者の手の内にある。守る対象が違う。
 */
Java.perform(function () {
    var ActivityThread = Java.use('android.app.ActivityThread');
    var context = ActivityThread.currentApplication().getApplicationContext();
    var prefs = context.getSharedPreferences('tee_demo', 0);

    var KeyStore = Java.use('java.security.KeyStore');
    var Cipher = Java.use('javax.crypto.Cipher');
    var GCMParameterSpec = Java.use('javax.crypto.spec.GCMParameterSpec');
    var Base64 = Java.use('android.util.Base64');
    var String_ = Java.use('java.lang.String');

    var ks = KeyStore.getInstance('AndroidKeyStore');
    ks.load(null);

    ['tee_demo_key', 'tee_demo_key_auth'].forEach(function (alias) {
        console.log('\n=== alias = ' + alias + ' ===');

        var blob = prefs.getString('blob_' + alias, null);
        if (blob === null) {
            console.log('[!] この鍵の暗号文がない。アプリの画面で先に暗号化すること。');
            return;
        }

        var key = ks.getKey(alias, null);
        if (key === null) {
            console.log('[!] 鍵が存在しない。アプリの画面で先に作ること。');
            return;
        }
        console.log('[*] 鍵ハンドルを取得: ' + key.$className);
        console.log('[*] 鍵の中身 getEncoded() = ' + key.getEncoded() +
                    '  ← null なら、この API からは鍵バイト列を取れていない');

        var raw = Base64.decode(blob, 2);
        var iv = Java.array('byte', Array.prototype.slice.call(raw, 0, 12));
        var body = Java.array('byte', Array.prototype.slice.call(raw, 12));

        try {
            var cipher = Cipher.getInstance('AES/GCM/NoPadding');
            cipher.init(2, key, GCMParameterSpec.$new(128, iv));
            var plain = String_.$new(cipher.doFinal(body));
            console.log('[*] 復号結果 = ' + plain);
            console.log('[*] 鍵は取り出せていないのに、平文は手に入った。');
        } catch (e) {
            console.log('[*] 復号できなかった: ' + e);
            console.log('[*] 断っているのはアプリのコードではなく Keystore 側。');
        }
    });

    console.log('\n[*] getEncoded() が null なのは «この API から鍵バイト列を取り出せない»');
    console.log('    ということだけを意味する。ハードウェア保護の証明ではない。');
    console.log('    実際の保護レベルはアプリの「鍵がどこにあるか調べる」で確認すること。');
});
