# Dodiy Password Manager

An Android app that keeps your logins (Google, Microsoft, Apple, Facebook, GitHub, Malaysian banks and 3,500 other providers) encrypted on your phone. Pick the provider when you add a login and its logo is shown next to the name.

## Features

- **Unlock with fingerprint or master password**: either one opens the vault. Fingerprint unlock is offered after you create the vault and can be switched on or off in ⋮ → Fingerprint unlock. After 5 wrong passwords the app makes you wait 30 seconds (longer each time).
- **Provider logos**: over 3,500 providers to choose from, with the most common (Google, Microsoft, Apple, Facebook, GitHub, X, LinkedIn, Amazon …) at the top, then 100 Malaysian banks, e-wallets, telcos, government services, utilities, airlines, shops and insurers using their own app icons (see `assets/malaysia.tsv`), and a search box for the rest. Typing a name such as "Gmail" or "Twitter" picks the matching logo by itself. The full list is in [PROVIDERS.md](PROVIDERS.md); ⋮ → All provider logos shows them in the app.
- **Logins**: name, username or email, password, website and notes. Copy the username or password with one tap, show or hide the password, open the website.
- **More fields**: add as many extra fields as you need from a list of standard identity and access fields (see below), or your own named fields. Hidden fields are masked until you tap Show. A "2FA secret key" field shows the live 6-digit code, like an authenticator app.
- **Password generator**: 8 to 64 characters, choose upper case, lower case, numbers and symbols.
- **Encrypted backup**: ⋮ → Export encrypted backup saves a `.dpmbackup` file protected by a password you choose. ⋮ → Restore from backup adds the logins from a backup file (useful on a new phone).
- **Change master password** from the ⋮ menu (needs the current password).
- **Auto-lock** as soon as you leave the app, the 🔒 button locks at once, screenshots and the recent-apps preview are blocked, and copied passwords are cleared from the clipboard after 45 seconds.
- Light and dark theme follow the phone.

Needs Android 9 or newer. Fingerprint unlock needs a fingerprint (or a face unlock that Android rates as "strong") set up in the phone's settings.

## Standard fields

| Group | Fields |
| --- | --- |
| Account | Email, Phone number, Account ID / Customer ID, Member / Membership number, Backup email, Recovery phone, Login URL |
| Security | PIN, Transaction PIN / TAC, Secure word / security phrase, Security question, Security answer, 2FA secret key (one-time codes), Recovery / backup codes, Passkey / security key name, Seed / recovery phrase |
| Developer & IT access | API key, Client ID, Client secret, Tenant ID / domain, Access token, Server / host, Port, Database name, SSH private key, Wi-Fi network name, Wi-Fi password, Licence / product key |
| Bank & payment | Account number, Card number, Name on card, Card expiry (MM/YY), CVV, Card PIN, DuitNow ID, SWIFT / branch code |
| Personal ID | Full name, MyKad / IC number, Passport number, Passport expiry, Date of birth, Driving licence number, Tax number (TIN), Address |
| Other | Note, Custom field (text), Custom field (hidden) |

PINs, answers, keys, card numbers, IC and passport numbers are hidden fields. Tap a field's name to rename it.

## Security

```
vault.bin  = AES-256-GCM(vault key, your logins)
meta.json  pw  = AES-256-GCM( PBKDF2-SHA256(master password, 310,000 rounds), vault key )
           bio = Android-Keystore-AES-GCM( vault key )      only when fingerprint unlock is on
```

The fingerprint key lives in the Android Keystore (hardware-backed on most phones) and only works right after a successful strong biometric. Android deletes it when fingerprints are added or removed; the app then switches fingerprint unlock off and asks for the master password, after which you can turn it on again. The vault files are excluded from Android cloud backup and device transfer, so use the encrypted backup to move to a new phone.

Vaults made by version 0.1.0002 (which needed fingerprint and password together) are upgraded the first time you unlock: fingerprint, then password, once.

There is no way to recover a forgotten master password, by design.

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

Malaysian provider icons in `assets/logos/` are downloaded by the **Fetch provider logos** workflow (`tools/fetch_logos.py`, list in `assets/malaysia.tsv`). Add a line to the list and push to the `tools/fetch-logos` branch to fetch more. A provider without an icon gets a brand-coloured letter badge.

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

Brand logos from [Simple Icons](https://simpleicons.org) (CC0, see `assets/simple-icons-LICENSE.txt`); Malaysian provider icons are the providers' own website / app icons. All brand names and logos are trademarks of their respective owners and are used only to identify the service a login belongs to.
