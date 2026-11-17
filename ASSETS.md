# Artwork

These files are the app's identity, and are kept apart from its code. Like the code, they are all rights reserved.

## What this covers

- **The launcher icon, the TV banner and the companion icon:**
  `app/src/main/res/drawable/ic_launcher_background.xml`, `ic_launcher_foreground.xml`,
  `ic_launcher_monochrome.xml`, `ic_companion.xml`, `ic_notification.xml` and `tv_banner.xml`, and
  `app/src/main/res/mipmap-anydpi-v26/` (`ic_launcher.xml` and `ic_launcher_round.xml`).

- **The wordmark on the TV banner:** the "Ephyra" paths in `tv_banner.xml` are outlines of the letters E, p, h, y, r and a from
  the Outfit typeface (`design-system/designsystem/src/main/res/font/outfit.ttf`, weight 700), which is copyright its authors and
  under the SIL Open Font License 1.1 (`Outfit-OFL.txt` beside it). A vector cannot use a font, so the outlines are converted to
  path data. The licence covers the typeface and the outlines taken from it; the arrangement of the banner, and the name, are
  covered by the terms below.

## What this does not cover

Everything else in this repository is the code, which is all rights reserved too (see [LICENSE](LICENSE)), including the screenshot
reference images under `app/src/screenshotTestDebug/`, which are test fixtures.

## Terms

All rights reserved. The app's name and the artwork are the owner's alone.

The only permission is this: you may redistribute these files, unmodified, as part of an unmodified
copy of the app. A fork, or any modified build, must replace every file listed above with its own,
and must not use the name (see [TRADEMARKS.md](TRADEMARKS.md)).

See also [TRADEMARKS.md](TRADEMARKS.md).
