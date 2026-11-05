# Dodiy Password Manager

An Android app that keeps your logins (Google, Microsoft, Apple, Facebook, GitHub and 3,500 other providers) encrypted on your phone. Pick the provider when you add a login and its logo is shown next to the name.

## Features

- **Two-factor unlock**: every time you open the app you need your fingerprint (step 1) **and** your master password (step 2). Both are needed to decrypt the vault, not just to get past a screen (see Security below).
- **Provider logos**: 3,507 providers to choose from, with the 50 most common (Google, Microsoft, Apple, Facebook, GitHub, X, LinkedIn, Amazon …) at the top and a search box for the rest. Typing a name such as "Gmail" or "Twitter" picks the matching logo by itself. The full list is in [PROVIDERS.md](PROVIDERS.md); ⋮ → All provider logos shows them in the app.
- **Logins**: name, username or email, password, website and notes. Copy the username or password with one tap, show or hide the password, open the website.
- **Password generator**: 8 to 64 characters, choose upper case, lower case, numbers and symbols.
- **Encrypted backup**: ⋮ → Export encrypted backup saves a `.dpmbackup` file protected by a password you choose. ⋮ → Restore from backup adds the logins from a backup file (useful on a new phone).
- **Change master password** from the ⋮ menu (needs the current password and your fingerprint).
- **Auto-lock** as soon as you leave the app, the 🔒 button locks at once, screenshots and the recent-apps preview are blocked, and copied passwords are cleared from the clipboard after 45 seconds.
- Light and dark theme follow the phone.

Needs Android 9 or newer and a fingerprint (or a face unlock that Android rates as "strong"), set up in the phone's settings.

## Security

```
vault.bin  = AES-256-GCM(vault key, your logins)
vault key  is stored as  Keystore-AES-GCM( AES-256-GCM( PBKDF2-SHA256(master password, 310,000 rounds), vault key ) )
```

The outer layer uses a key inside the Android Keystore that only works right after a successful strong biometric, so the vault cannot be opened with the master password alone, and a fingerprint alone only gets the password-protected layer. After 5 wrong master passwords the fingerprint is needed again. The vault files are excluded from Android cloud backup and device transfer, so use the encrypted backup to move to a new phone.

If Android deletes the fingerprint key (for example when the screen lock is turned off), the vault can no longer be opened. Reset it from the lock screen and restore your latest backup. There is no way to recover a forgotten master password, by design.

## Versions and updates

`versionName` is `0.1.NNNN` and `versionCode` is `NNNN`, from `BUILD_NUMBER` in `version.properties`. NNNN is the total number of tasks requested in the project thread so far, so set it to that count for each release (`release.sh` only adds 1).

Every release is signed with the same key and keeps the package name `com.dodiy.passwordmanager`, so a new APK installs over the old one without uninstalling and your saved logins are kept. The keystore is kept outside this repo; never replace it, or updates will stop installing.

## Building

The app uses only the Android framework (no Gradle, no AndroidX), written in Java 8 without lambdas, like Grocery List.

Tools: JDK 17+, `android-sdk-build-tools` (aapt2, zipalign, apksigner) and `dalvik-exchange` (dx) from Debian/Ubuntu, plus `android.jar` for API 35 (compile) and API 33 (resource linking).

```sh
./release.sh     # bump version, build, sign, copy to the releases folder
./build.sh       # build and sign the current version only
test/run.sh      # plain-JVM tests: encryption layers, backup, generator, all logo paths
```

Paths can be overridden with `ANDROID_SDK`, `BUILD_TOOLS`, `PM_SIGNING_PROPS` and `RELEASE_DIR`.

The logo data in `assets/providers.txt` is made from the `simple-icons` npm package:

```sh
python3 -I tools/convert_icons.py path/to/simple-icons assets/providers.txt   # needs: pip install svgelements
python3 -I tools/list_providers.py                                            # rewrites PROVIDERS.md
```

## GitHub Actions

`.github/workflows/build.yml` runs the tests and builds the APK on every push and pull request. On `main` it signs with the release key and publishes a GitHub release `v0.1.NNNN` (once per version, so bump `BUILD_NUMBER` to publish a new one). Pull requests are signed with a throwaway key and only uploaded as a build artifact.

Repository secrets it needs (Settings → Secrets and variables → Actions):

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | the release keystore, base64 encoded (`base64 -w0 release.jks`) |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | `passwordmanager` |
| `KEY_PASSWORD` | key password |

## Credits

Brand logos from [Simple Icons](https://simpleicons.org) (CC0, see `assets/simple-icons-LICENSE.txt`). All brand names and logos are trademarks of their respective owners and are used only to identify the service a login belongs to.
