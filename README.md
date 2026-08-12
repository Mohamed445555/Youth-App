# السجل — Android App

This is your registry app, now a real Android app with on-device SQLite,
Excel export/import, and a one-tap "export + send to WhatsApp" button.

You do **not** need Android Studio. GitHub will build the installable
`.apk` file for you automatically — you just download it.

## One-time setup (10 minutes)

1. **Create a new GitHub repository**
   Go to https://github.com/new → name it anything (e.g. `registry-app`) →
   choose **Private** if you don't want it public → click **Create repository**.
   Leave it empty (don't add a README there).

2. **Upload this whole folder to that repository**
   Easiest way, no command line needed:
   - On your new repo's page, click **"uploading an existing file"**.
   - Drag the *entire contents* of this folder in (keep the folder structure —
     `.github`, `app`, `build.gradle`, `settings.gradle`, `gradle.properties`,
     and **`debug.keystore`** all need to be at the top level of the repo).
   - Commit the upload (the big green button).

   > ⚠️ **Don't skip `debug.keystore`.** It's the file that signs every build.
   > As long as it stays in the repo, future rebuilds produce APKs that can
   > install *over* older ones (so team members can update in place instead
   > of uninstalling first). If it's ever missing on a rebuild, a new random
   > key gets used and everyone would need to uninstall the old app first.

3. **Let GitHub build the APK**
   - Go to the **Actions** tab of your repo.
   - You should see a workflow called **"Build APK"** running automatically
     (it starts on every upload). If it doesn't start, click **"Build APK"**
     on the left, then **"Run workflow"**.
   - Wait 3–5 minutes for it to finish (green checkmark).

4. **Download the APK**
   - Click on the finished workflow run.
   - Scroll down to **Artifacts** → download **registry-apk**.
   - It downloads as a `.zip` — open it, and inside is `app-release.apk`.

5. **Install it on a phone**
   - Send that `.apk` file to the phone (WhatsApp to yourself, email, USB, etc).
   - Open it on the phone. Android will warn about "unknown sources" the
     first time — tap **Settings → Allow from this source**, then install.
   - Repeat for each team member's phone, or just send them the same APK file.

That's the whole flow — no Android Studio, no paid Google Play listing,
no backend server.

## What changed from your HTML file

- **Storage:** real on-device SQLite (via Android's built-in database engine)
  instead of sql.js/IndexedDB. Faster, and works fully offline — no CDN fonts
  or libraries are loaded from the internet anymore.
- **Excel export:** tap "تصدير إلى إكسل" → pick where to save → done.
- **Excel import:** tap "استيراد من إكسل" → pick the file → same duplicate-safe
  logic as before (skips rows matching an existing ID number or registry ID).
- **New: "إرسال عبر واتساب" button.** One tap: builds the Excel file, then
  opens WhatsApp with the file attached and the chat pre-opened to
  `0934111001`. **You (or whoever taps it) still has to press Send inside
  WhatsApp** — WhatsApp itself blocks apps from sending messages silently on
  someone's behalf, so a fully automatic send isn't possible on any app, not
  just this one. It's one tap away though.

## Each team member's device code

On first launch, the app asks for a short device code (their initials).
**Make sure every team member gets a genuinely unique code** — if two people
are both given "AH", their registry IDs will collide (`AH-0001` from each),
and when you later import both of their Excel files into your master copy,
the second person's colliding rows will be silently skipped as "duplicates."
Simple fix: use full initials or add a digit (`AH1`, `AH2`) if two people
share initials.

## Rebuilding after a future change

Any time this project's code changes, just re-upload the changed files to
the same GitHub repo (or push via git if you're comfortable with that) —
the Actions workflow re-runs automatically and a new APK appears in
Artifacts within a few minutes.

## Known limitations (by design, not bugs)

- **No live sync between phones.** Each phone has its own separate database.
  Your plan of collecting Excel exports and importing them into one master
  copy is exactly how this is meant to be used — see the in-app export/import
  buttons.
- **WhatsApp send needs one manual tap** — see above, this is a WhatsApp
  platform restriction, not a limitation of this app.
- **The exported .xlsx is a minimal, valid Excel file** (opens fine in Excel,
  Google Sheets, LibreOffice) but doesn't include cell styling/colors —
  just clean data in columns, matching your original export's columns.
