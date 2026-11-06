# Read Aloud

Android app: read a book aloud, word by word. Text starts grey; the word to say is highlighted;
when the speech recognizer hears it, it turns dark and the next word lights up.

- **Tap a word**: move the highlight there.
- **Double-tap / hold a word**: popup from the PDF IPA Reader extension: US IPA (tap a symbol for its guide card),
  Spanish, Pronounce / Slow, Save. Saved words + look-up box: star icon. Copy the list from there.
- **Wrong word**: stays highlighted until said right. Reading on past it marks it amber (skipped). "Skip word" does the same by hand.
- Progress per book is saved on the phone.

## Getting a book in

1. **PC (main way, cleaner text)**: `cd pipeline && py book.py path/to/book.pdf` → `book.readaloud.json`.
   Same clean-up as Pronunciation Coach (bookmarks → chapters, headers/captions/tables/citations removed,
   end-of-chapter questions dropped). `--chapters 1-3`, `--list`, `--include "key terms"`. Copy the file to the phone → **Open a book**.
2. **Phone (backup)**: **Open a PDF** reads it on the phone (PdfBox), with simpler clean-up. Big books are slow (≈3 min for a 57 MB, 700-page book).

## Build
Same as Pronunciation Coach: the path has a non-ASCII character, so build from the `P:` drive (`subst P: "<parent folder>"`):
`cd P:\read-aloud\android-app && gradlew assembleDebug`. Package `com.zeit2206.readaloud`.

Debug hooks (emulator): `adb shell am start -n com.zeit2206.readaloud/.MainActivity --es pdf <file in app's external files dir>` imports it;
`--es say "words"` acts as if the mic heard them.

## Known limits
- The recognizer guesses whole phrases, so it sometimes "fixes" a mispronounced word into the right one.
- Each listening session restarts after a pause; some phones beep at each restart.
- Assets copied (not linked): `ipa.tsv` from Pronunciation Coach, `es.tsv` converted from the extension's `en-es.json`.
