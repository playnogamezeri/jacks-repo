# Toon Jack signing key setup

Toon Jack Android updates must be signed with the same private keystore for every release. Android rejects an update signed with a different key. GitHub Actions caches are temporary and can be evicted, so the build no longer treats a cache as the permanent signing-key store.

## One-time setup

Create a dedicated keystore on a trusted computer. Keep the keystore and passwords private; do not commit them to this repository.

```bash
keytool -genkeypair -v \
  -keystore toonjack-upload.jks \
  -alias toonjack \
  -keyalg RSA -keysize 2048 -validity 10000
```

Use the same store password and key password when prompted, or record both separately. Do not lose this file. Back it up securely.

Create the Base64 value:

- Linux: `base64 -w 0 toonjack-upload.jks`
- macOS: `base64 < toonjack-upload.jks | tr -d '\n'`
- PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("toonjack-upload.jks"))`

Get the certificate SHA-256 fingerprint (enter the store password when prompted):

```bash
keytool -exportcert -keystore toonjack-upload.jks -alias toonjack | sha256sum
```

In GitHub, open **Settings → Secrets and variables → Actions → New repository secret** and create these five secrets:

| Secret name | Value |
|---|---|
| `TOONJACK_KEYSTORE_BASE64` | Base64 output for the keystore |
| `TOONJACK_STORE_PASSWORD` | Keystore password |
| `TOONJACK_KEY_ALIAS` | `toonjack` |
| `TOONJACK_KEY_PASSWORD` | Password for the key alias |
| `TOONJACK_SIGNING_CERT_SHA256` | SHA-256 fingerprint, hexadecimal only |

Then run **Actions → Build Toon Jack Android APK → Run workflow**. The workflow verifies the key fingerprint before building and verifies the APK signature again before publishing the artifact.

## Future updates and key rotation

- Keep using this exact keystore and alias for every update. Do not generate a new key for routine releases.
- Keep secure offline backups of the keystore and passwords. GitHub Secrets are not a substitute for your own backup.
- If the signing key is intentionally rotated, Android will not accept the new APK as an in-place update over an installation signed by the old key. Users must uninstall the old app (which can erase app-private recordings and lyrics) or use a supported Android signing-key upgrade mechanism. Never rotate casually.
- Increase `versionCode` for each published APK.

The repository does not contain the private keystore.
