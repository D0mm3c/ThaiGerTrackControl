# Printable Guidebook (LaTeX)

`guidebook.tex` produces a single **print-ready PDF** containing the full ThaiGer
Track Control user guide in **six languages**, one part per language with a shared
title page and table of contents:

**English · Deutsch · Français · Español · Русский · 中文 (简体)**

A pre-built `guidebook.pdf` (58 pages, A4) is included so you can print right away.

## Requirements

- A TeX distribution: **MiKTeX** (Windows) or **TeX Live** (any OS).
- The **XeLaTeX** engine — required for the Chinese/CJK and Cyrillic text and for
  the system fonts. (Do *not* use pdfLaTeX; it cannot embed these fonts.)

## Build

```bash
xelatex guidebook.tex
xelatex guidebook.tex      # run twice so the table of contents resolves
```

or, simpler:

```bash
latexmk -xelatex guidebook.tex
```

On the first run MiKTeX may prompt to install missing packages (xeCJK, tcolorbox,
babel language files, etc.) — accept. The result is `guidebook.pdf`.

## Fonts

| Role | Font | Covers |
|---|---|---|
| Main text | `Times New Roman` | Latin, Western-European accents, **Cyrillic** (Russian) |
| Chinese (CJK) | `SimSun` (宋体) + `Microsoft YaHei` (微软雅黑) | Simplified Chinese |
| Code / monospace | Latin Modern Mono (bundled) | code tokens, JSON, URLs |

All of these ship with **Windows 11**. The Latin main font must include Cyrillic
because the Russian section titles also appear in the (Latin-typeset) table of
contents and PDF bookmarks.

**On Linux / macOS** edit the two font lines near the top of `guidebook.tex`:

```latex
\setmainfont{TeX Gyre Termes}     % or "Noto Serif" — both include Cyrillic
% ...
\setCJKmainfont{Noto Serif CJK SC}
\setCJKsansfont{Noto Sans CJK SC}
\setCJKmonofont{Noto Sans Mono CJK SC}
```

(`TeX Gyre Termes` is a Times-compatible font bundled with every TeX distribution.)

## Notes

- All six language parts live in the one source file for printing as a single
  booklet. To print just one language, comment out the other `PART …` blocks
  (between the big `% ####` banners) before building.
- Language hyphenation is handled by `babel` with `shorthands=off`, so characters
  like `"`, `:`, `<`, `>` stay literal inside code tokens and URLs.
- The digital/web version of the same content (English, German, Chinese) lives one
  folder up: [`../GUIDEBOOK.en.md`](../GUIDEBOOK.en.md),
  [`../GUIDEBOOK.de.md`](../GUIDEBOOK.de.md),
  [`../GUIDEBOOK.zh.md`](../GUIDEBOOK.zh.md).
