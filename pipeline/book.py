"""PDF -> one cleaned book file for the Read Aloud app (<pdf>.readaloud.json).

Uses the same cleanup as Pronunciation Coach (chunk.py / clean.py): chapters from the PDF
bookmarks, running headers, figure captions, tables, links and citations removed, and
end-of-chapter material (questions, key terms...) dropped unless --include'd.

Usage:
  python book.py book.pdf                      # whole book
  python book.py book.pdf --chapters 1-3       # some chapters
  python book.py book.pdf --list               # show the plan only

Copy the .json to the phone and open it in the app ("Open a book").
Format (also written by the app's own on-phone PDF import):
  {"format": "readaloud/1", "title": str, "paras": [str], "pages": [0-based page per paragraph],
   "toc": [{"t": title, "p": paragraph index, "l": level 0|1}]}
"""
import argparse
import json
import re
import sys
from pathlib import Path

import pymupdf
import pymupdf4llm

from chunk import DEFAULT_EXCLUDE, FRONT_BACK_MATTER, chapters_from_toc, drop_sections, health_check, matches, norm, parse_ranges
from clean import MARGINS, clean

# OpenStax box labels whose contents (links, figures) are already removed.
BOX_LABELS = {"LINK TO LEARNING", "EVERYDAY CONNECTION", "DIG DEEPER", "WHAT DO YOU THINK?"}
SENTENCE_END = re.compile(r"[.!?:\"”’)]$")


def page_paragraphs(doc, p0, p1, sections, exclude):
    """[(paragraph, page0)] for pages p0..p1, joining paragraphs split by a page break."""
    chunks = pymupdf4llm.to_markdown(doc, pages=list(range(p0, p1 + 1)), ignore_images=True,
                                     margins=MARGINS, page_chunks=True)
    out = []
    # drop_sections works on whole text; run it per page while carrying the "skipping" state.
    keys = {norm(s): matches(s, exclude) for s in sections}
    skipping = False
    for ch in chunks:
        page = ch["metadata"]["page"] - 1 if "page" in ch["metadata"] else ch["metadata"].get("page_number", 1) - 1
        kept = []
        for line in clean(ch["text"]).splitlines():
            k = norm(line)
            if k in keys:
                skipping = keys[k]
            if not skipping:
                kept.append(line)
        for i, para in enumerate(p.strip() for p in "\n".join(kept).split("\n\n")):
            para = re.sub(r"\s*\n\s*", " ", para)
            if not re.search(r"[A-Za-z]", para) or para.upper() in BOX_LABELS:
                continue
            if i == 0 and out and not SENTENCE_END.search(out[-1][0]) and para[0].islower():
                out[-1] = (out[-1][0] + " " + para, out[-1][1])
            else:
                out.append((para, page))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("pdf")
    ap.add_argument("--out", help="output file (default: <pdf>.readaloud.json)")
    ap.add_argument("--chapters", help="1-based chapter numbers, e.g. 1-3,7")
    ap.add_argument("--include", default="", help="comma list of normally dropped section names to keep")
    ap.add_argument("--exclude", default="", help="comma list of extra section names to drop")
    ap.add_argument("--list", action="store_true")
    ap.add_argument("--pages-per-chunk", type=int, default=30, help="part size when the PDF has no bookmarks")
    a = ap.parse_args()

    include = [s.strip().lower() for s in a.include.split(",") if s.strip()]
    exclude = [p for p in DEFAULT_EXCLUDE if not any(p in i or i in p for i in include)]
    exclude += [s.strip().lower() for s in a.exclude.split(",") if s.strip()]

    pdf = Path(a.pdf)
    doc = pymupdf.open(pdf)
    chapters = [c for c in chapters_from_toc(doc) if not matches(c[0], FRONT_BACK_MATTER)]
    if not chapters:
        n = a.pages_per_chunk
        print(f"No bookmarks: splitting into {n}-page parts.")
        chapters = [(f"Part {i // n + 1}", i, min(i + n, doc.page_count) - 1, []) for i in range(0, doc.page_count, n)]
    wanted = parse_ranges(a.chapters) if a.chapters else None
    chosen = [(n, c) for n, c in enumerate(chapters, 1) if not wanted or n in wanted]
    for w in health_check(doc, [p for _, c in chosen for p in range(c[1], c[2] + 1)]):
        print("WARNING:", w)

    paras, pages, toc = [], [], []
    for num, (title, p0, p1, sections) in chosen:
        skipped = [s for s in sections if matches(s, exclude)]
        if a.list:
            print(f"{num:3d}. {title}  (pp. {p0 + 1}-{p1 + 1})" + (f"\n      skip: {'; '.join(skipped)}" if skipped else ""))
            continue
        start = len(paras)
        toc.append({"t": title, "p": start, "l": 0})
        section_keys = {norm(s): s for s in sections if not matches(s, exclude)}
        for para, page in page_paragraphs(doc, p0, p1, sections, exclude):
            if norm(para) in section_keys and section_keys[norm(para)] not in [t["t"] for t in toc if t["p"] >= start]:
                toc.append({"t": section_keys[norm(para)], "p": len(paras), "l": 1})
            paras.append(para)
            pages.append(page)
        print(f"{num:3d}. {title}: {sum(len(p.split()) for p in paras[start:])} words, skipped {len(skipped)} section(s)")
    if a.list:
        return

    meta_title = (doc.metadata or {}).get("title") or pdf.stem
    out = Path(a.out) if a.out else pdf.with_suffix(".readaloud.json")
    out.write_text(json.dumps({"format": "readaloud/1", "title": meta_title, "paras": paras, "pages": pages, "toc": toc},
                              ensure_ascii=False), encoding="utf-8")
    print(f"{out}: {len(paras)} paragraphs, {sum(len(p.split()) for p in paras)} words, {len(toc)} contents entries")


if __name__ == "__main__":
    sys.exit(main())
