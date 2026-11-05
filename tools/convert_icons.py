"""Convert Simple Icons SVGs into a compact absolute M/L/C/Z path list for the app.
Usage: python3 -I convert_icons.py <simple-icons package dir> <out file>"""
import json, os, re, sys
from svgelements import Path, Move, Line, QuadraticBezier, CubicBezier, Arc, Close

pkg, out = sys.argv[1], sys.argv[2]
data = json.load(open(os.path.join(pkg, 'data/simple-icons.json')))
data = data if isinstance(data, list) else data['icons']

def slugify(title):
    # same rules as simple-icons titleToSlug
    t = title.lower()
    for a, b in [('+', 'plus'), ('.', 'dot'), ('&', 'and')]:
        t = t.replace(a, b)
    import unicodedata
    t = unicodedata.normalize('NFD', t)
    t = ''.join(c for c in t if not unicodedata.combining(c))
    return re.sub(r'[^a-z0-9]', '', t)

def f(v):
    s = ('%.2f' % v).rstrip('0').rstrip('.')
    return '0' if s in ('-0', '') else s

def conv(d):
    p = Path(d)
    out = []
    for seg in p:
        if isinstance(seg, Move):
            out.append('M%s %s' % (f(seg.end.x), f(seg.end.y)))
        elif isinstance(seg, Close):
            out.append('Z')
        elif isinstance(seg, Line):
            out.append('L%s %s' % (f(seg.end.x), f(seg.end.y)))
        elif isinstance(seg, CubicBezier):
            out.append('C%s %s %s %s %s %s' % tuple(f(v) for v in (seg.control1.x, seg.control1.y, seg.control2.x, seg.control2.y, seg.end.x, seg.end.y)))
        elif isinstance(seg, QuadraticBezier):
            s, c, e = seg.start, seg.control, seg.end
            c1 = (s.x + 2/3*(c.x-s.x), s.y + 2/3*(c.y-s.y)); c2 = (e.x + 2/3*(c.x-e.x), e.y + 2/3*(c.y-e.y))
            out.append('C%s %s %s %s %s %s' % tuple(f(v) for v in (c1[0], c1[1], c2[0], c2[1], e.x, e.y)))
        elif isinstance(seg, Arc):
            if seg.start == seg.end:
                continue
            for cb in seg.as_cubic_curves():
                out.append('C%s %s %s %s %s %s' % tuple(f(v) for v in (cb.control1.x, cb.control1.y, cb.control2.x, cb.control2.y, cb.end.x, cb.end.y)))
        else:
            raise ValueError(type(seg))
    return ''.join(out)

n = 0; bad = []
with open(out, 'w', encoding='utf-8') as o:
    for ic in sorted(data, key=lambda x: x['title'].lower()):
        slug = ic.get('slug') or slugify(ic['title'])
        fn = os.path.join(pkg, 'icons', slug + '.svg')
        if not os.path.exists(fn):
            bad.append(slug); continue
        svg = open(fn, encoding='utf-8').read()
        m = re.search(r'<path d="([^"]+)"', svg)
        if not m:
            bad.append(slug); continue
        al = ic.get('aliases') or {}
        names = list(al.get('aka', [])) + [d['title'] for d in al.get('dup', [])] + list((al.get('loc') or {}).values())
        names = [x.replace('\t', ' ').replace('|', ' ') for x in names]
        try:
            path = conv(m.group(1))
        except Exception as e:
            bad.append(slug + ':' + str(e)); continue
        o.write('\t'.join([slug, ic['title'].replace('\t', ' '), ic['hex'].upper(), '|'.join(names), path]) + '\n')
        n += 1
print('icons', n, 'skipped', bad[:20], len(bad))
