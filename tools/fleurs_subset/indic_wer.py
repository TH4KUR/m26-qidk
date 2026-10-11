"""Hindi/Telugu-safe text normalisation and error rates.

accent_contest/contest.py normalises with `[^\\w\\s]`, which removes Indic vowel signs and the virama (they are not "word"
characters in Python's regex), so a Hindi or Telugu word is cut into single consonants and WER is meaningless. This module keeps
letters (L*), combining marks (M*) and digits (N*), turns punctuation (including the danda) into a space, and drops only
invisible format characters (ZWJ/ZWNJ and the like). It does not transliterate, stem or merge spellings.
"""
import unicodedata


def normalize(text: str) -> list[str]:
    out = []
    for ch in unicodedata.normalize("NFC", text).casefold():
        category = unicodedata.category(ch)
        if category[0] in "LMN":
            out.append(ch)
        elif category == "Cf":
            continue  # zero-width joiner / non-joiner: not a word boundary
        else:
            out.append(" ")  # punctuation, symbols, spaces, controls
    return "".join(out).split()


def edit_distance(a: list, b: list) -> int:
    previous = list(range(len(b) + 1))
    for i, x in enumerate(a, start=1):
        current = [i]
        for j, y in enumerate(b, start=1):
            current.append(min(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + (x != y)))
        previous = current
    return previous[-1]


def wer(reference: str, hypothesis: str) -> tuple[int, int]:
    """(word errors, reference words)."""
    ref, hyp = normalize(reference), normalize(hypothesis)
    return edit_distance(ref, hyp), len(ref)


def cer(reference: str, hypothesis: str) -> tuple[int, int]:
    """(character errors, reference characters) over the normalised text without spaces; a Unicode code point is one character."""
    ref, hyp = list("".join(normalize(reference))), list("".join(normalize(hypothesis)))
    return edit_distance(ref, hyp), len(ref)
