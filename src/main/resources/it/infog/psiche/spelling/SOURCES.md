# The spelling dictionaries

Hunspell dictionaries from LibreOffice's `dictionaries` repository (https://github.com/LibreOffice/dictionaries, a
mirror of https://git.libreoffice.org/dictionaries), copied unchanged at commit
`4f1df64b2e8e14751aa7c2205d170ca016071315` (27 October 2026), each with the README that states its authors and
licence.

| Folder | Upstream files | Dictionary | Licence |
|---|---|---|---|
| `en` | `en/en_US.aff`, `en/en_US.dic`, `en/README_en_US.txt` | English (US), from SCOWL, 2020.12.07 | SCOWL's permissive notice; the affix file under Geoff Kuenning's BSD licence |
| `it` | `it_IT/it_IT.aff`, `it_IT/it_IT.dic`, `it_IT/README_it_IT.txt` | Italian, LibreItalia, 5.1.1 | GPL 3 |
| `fr` | `fr_FR/dictionaries/fr.aff`, `fr.dic`, `README_dict_fr.txt` | French "Classique", Grammalecte, 7.7 | MPL 2.0 |
| `es` | `es/es_ES.aff`, `es/es_ES.dic`, `es/README_hunspell_es.txt`, `es/LICENSE.md` | Spanish (Spain), RLA-ES | GPL 3 or later, LGPL 3 or later, or MPL 1.1 or later: used under the GPL 3 or later |
| `de` | `de/de_DE_frami.aff`, `de/de_DE_frami.dic`, `de/README_de_DE_frami.txt` | German (Germany), igerman98 with frami | GPL 2 or 3: used under the GPL 3 |

To update one, copy its files from a later commit, note that commit here, and run the spelling tests.
