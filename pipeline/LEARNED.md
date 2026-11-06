# Learned patterns

One line per quirk: book/publisher → problem → fix. Newest last.

- OpenStax (Psychology 2e) → headers "8  1 • Title" / "1.2 • Title  9" not removed by `margins` → `RUNNING_HEADER` regex in clean.py
- OpenStax → footer "Access for free at openstax.org" → dropped in clean.py
- OpenStax → "Link to Learning" boxes with URLs → drop any line containing "http"
- OpenStax → run-in labels "**INTRODUCTION** text…" → strip label, keep text
- Any → exclude pattern "problems" hit real sections ("8.3 Problems with Memory") → whole-title matching, no generic words
- Practice phrases (tts.py) → cuts after prepositions/articles ("have of | that") → NO_CUT_AFTER list; prefer strong conjunctions; keep ≤18-word clauses whole when no conjunction
- num2words → "two thousand, five hundred" (comma) → strip commas from spoken numbers
- OpenStax → all-caps box labels ("LINK TO LEARNING") survive as headings → skipped in tts.py plan()
