"""Writes PROVIDERS.md: every logo bundled in the app. Usage: python3 -I tools/list_providers.py (from the repo root)"""
import re

rows = []
for line in open('assets/providers.txt', encoding='utf-8'):
    slug, title, hex_, aliases, _ = line.rstrip('\n').split('\t')
    rows.append((title, slug, hex_, aliases.replace('|', ', '), 'logo'))
src = open('src/com/dodiy/passwordmanager/Providers.java', encoding='utf-8').read()
have = {r[1] for r in rows}
for slug, title, hex_, letters, aliases in re.findall(r'\{"([a-z0-9]+)", "([^"]+)", "([0-9A-F]{6})", "([^"]*)", "([^"]*)"\}', src):
    if slug not in have:
        rows.append((title, slug, hex_, aliases.replace('|', ', '), 'hand-drawn logo' if slug == 'microsoft' else 'brand-colour badge "%s"' % letters))
rows.sort(key=lambda r: r[0].lower())
popular = re.search(r'POPULAR = \{(.*?)\};', src, re.S).group(1)
popular = re.findall(r'"([a-z0-9]+)"', popular)
by = {r[1]: r for r in rows}

out = ['# Provider logos', '',
       'Every provider you can pick in the app (%d in total). Pick one when adding a login and its logo is shown next to the name.' % len(rows), '',
       'Logos come from [Simple Icons](https://simpleicons.org) (CC0). A few brands asked Simple Icons to remove their logo, so the app shows a brand-coloured badge with letters for those instead. All names and logos are trademarks of their owners.', '',
       '## Popular (shown first in the picker)', '']
out.append(', '.join(by[s][0] for s in popular if s in by))
out += ['', '## All providers A to Z', '', '| # | Provider | Also known as | Icon |', '|---|---|---|---|']
for i, r in enumerate(rows, 1):
    out.append('| %d | %s | %s | %s |' % (i, r[0].replace('|', '\\|'), r[3].replace('|', '\\|'), r[4]))
open('PROVIDERS.md', 'w', encoding='utf-8').write('\n'.join(out) + '\n')
print(len(rows), 'providers;', sum(1 for r in rows if r[4] != 'logo'), 'badges/hand-drawn')
