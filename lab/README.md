# PWA・中継サーバー

同じWi-Fi内のAndroid・iPhone・PCで、安否や救助記録の同期・中継を試す環境。サーバーは情報を保存しない。

## 起動

Node.js 24以上とOpenSSLが必要。

```powershell
npm ci
node scripts/setup.mjs
npm start
```

接続先は `.local/connection.txt` に出力される。端末の証明書設定は[実験手順](TESTING.md#証明書の設定)を参照。

秘密鍵・接続コードは `.local/` に置き、Gitに含めない。本人認証は未実装のため、架空の情報で試す。

## 検証と通信仕様

```powershell
npm test
```

- [端末の準備・中継試験](TESTING.md)
- [通信仕様](TESTING.md#検証と通信仕様)
- [Androidとの自動試験](../android/LAB_AUTOMATED_TESTING.md)
