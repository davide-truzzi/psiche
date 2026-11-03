# The spelling dictionaries

Hunspell dictionaries from LibreOffice's `dictionaries` repository (https://github.com/LibreOffice/dictionaries, a
mirror of https://git.libreoffice.org/dictionaries), at commit `4f1df64b2e8e14751aa7c2205d170ca016071315`
(27 October 2026). Their contents are unchanged; each `.aff` and `.dic` is renamed after its folder, and keeps the
README that states its authors and licence.

| Folder | Upstream files | Dictionary | Licence |
|---|---|---|---|
| `en-US` | `en/en_US.aff`, `en/en_US.dic`, `en/README_en_US.txt` | English (US), from SCOWL, 2020.12.07 | SCOWL's permissive notice; the affix file under Geoff Kuenning's BSD licence |
| `it-IT` | `it_IT/it_IT.aff`, `it_IT/it_IT.dic`, `it_IT/README_it_IT.txt` | Italian, LibreItalia, 5.1.1 | GPL 3 |
| `fr-FR` | `fr_FR/dictionaries/fr.aff`, `fr.dic`, `README_dict_fr.txt` | French "Classique", Grammalecte, 7.7 | MPL 2.0 |
| `es-ES` | `es/es_ES.aff`, `es/es_ES.dic`, `es/README_hunspell_es.txt`, `es/LICENSE.md` | Spanish (Spain), RLA-ES | GPL 3 or later, LGPL 3 or later, or MPL 1.1 or later: used under the GPL 3 or later |
| `de-DE` | `de/de_DE_frami.aff`, `de/de_DE_frami.dic`, `de/README_de_DE_frami.txt` | German (Germany), igerman98 with frami | GPL 2 or 3: used under the GPL 3 |
| `pt-BR` | `pt_BR/pt_BR.aff`, `pt_BR/pt_BR.dic`, `pt_BR/README_pt_BR.txt` | Portuguese (Brazil), VERO | LGPL 3 or MPL |
| `pt-PT` | `pt_PT/pt_PT.aff`, `pt_PT/pt_PT.dic`, `pt_PT/README_pt_PT.txt`, `pt_PT/LICENSES.txt` | Portuguese (Portugal), Universidade do Minho | GPL 2, LGPL 2.1 or MPL 1.1, in that order; LICENSES.txt also names GPL and BSD |

## Adding or removing a dictionary

1. Copy the `.aff` and `.dic` files, and the README or licence file, from the repository into a folder named after
   the dictionary's BCP 47 tag, such as `nl-NL`, renaming the two files after it: `nl-NL.aff`, `nl-NL.dic`. Check
   its licence first: it must allow its use in a GPL 3 app.
2. Add its line to `dictionaries.txt`, as `tag | source | licence`, below any other of its language it should give
   way to. About lists the dictionaries from there.
3. Add its row above, with the upstream commit if it differs.
4. Run the spelling tests: they check that the folders and `dictionaries.txt` match, and that each dictionary loads.

To remove one, delete its folder, its line in `dictionaries.txt` and its row here.

To update one, copy its files from a later commit, note that commit in its row, and run the tests.
