"""Downloads the logo / app icon of each provider in assets/malaysia.tsv and writes assets/logos/<slug>.png (128x128).

Runs on a GitHub Actions runner (the build sandbox has no general internet). Tries, in order of preference:
the site's apple-touch-icon / large <link rel=icon>, /apple-touch-icon.png, Google's favicon service, DuckDuckGo's.
Usage: python3 tools/fetch_logos.py assets/malaysia.tsv assets/logos tools/logo-report.json tools/logo-sheet.png
"""
import io, json, re, sys, urllib.parse, urllib.request
from html.parser import HTMLParser
from PIL import Image, ImageDraw

UA = 'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36'
SIZE = 128

def get(url, limit=3_000_000):
    req = urllib.request.Request(url, headers={'User-Agent': UA, 'Accept': '*/*'})
    with urllib.request.urlopen(req, timeout=20) as r:
        return r.geturl(), r.read(limit)

class Links(HTMLParser):
    def __init__(self):
        super().__init__(); self.icons = []
    def handle_starttag(self, tag, attrs):
        if tag != 'link': return
        a = dict((k.lower(), v or '') for k, v in attrs)
        rel = a.get('rel', '').lower()
        if 'icon' in rel and a.get('href'):
            m = re.findall(r'(\d+)x(\d+)', a.get('sizes', ''))
            size = max([int(x) for x, _ in m] or [0])
            score = size + (500 if 'apple-touch' in rel else 0)
            self.icons.append((score, a['href']))

def load(data):
    if data[:5] in (b'<?xml', b'<svg ') or b'<svg' in data[:300]:
        import cairosvg
        data = cairosvg.svg2png(bytestring=data, output_width=256, output_height=256)
    im = Image.open(io.BytesIO(data))
    if getattr(im, 'n_frames', 1) > 1 or im.format == 'ICO':   # ICO: take the largest frame
        best = None
        for s in sorted(getattr(im, 'ico', None).sizes() if im.format == 'ICO' else [im.size], reverse=True):
            if im.format == 'ICO': im.size = s
            best = im.copy(); break
        im = best or im
    im.load()
    return im.convert('RGBA')

def candidates(domain):
    out = []
    for base in ('https://www.' + domain + '/', 'https://' + domain + '/'):
        try:
            final, html = get(base, 800_000)
            p = Links(); p.feed(html.decode('utf-8', 'ignore'))
            for score, href in sorted(p.icons, reverse=True):
                out.append(('site:' + str(score), urllib.parse.urljoin(final, href), score))
            root = '{0.scheme}://{0.netloc}/'.format(urllib.parse.urlparse(final))
            out.append(('apple-touch-icon', root + 'apple-touch-icon.png', 180))
            break
        except Exception as e:
            print('  page fail', base, type(e).__name__, str(e)[:80])
    out.append(('google', 'https://www.google.com/s2/favicons?domain=%s&sz=256' % domain, 0))
    out.append(('duckduckgo', 'https://icons.duckduckgo.com/ip3/%s.ico' % domain, 0))
    return out

def compose(im):
    bbox = im.getchannel('A').point(lambda a: 255 if a > 16 else 0).getbbox()
    if bbox: im = im.crop(bbox)
    w, h = im.size
    px = im.load()
    corners = [px[0, 0], px[w - 1, 0], px[0, h - 1], px[w - 1, h - 1]]
    opaque = all(c[3] > 240 for c in corners)
    square = 0.9 <= w / h <= 1.1
    if opaque and square:
        # full-bleed app icon: fill the tile (the app clips it to a circle)
        return im.resize((SIZE, SIZE), Image.LANCZOS), 'full'
    canvas = Image.new('RGBA', (SIZE, SIZE), (255, 255, 255, 255))
    box = int(SIZE * 0.70)
    scale = box / max(w, h)
    im2 = im.resize((max(1, round(w * scale)), max(1, round(h * scale))), Image.LANCZOS)
    canvas.alpha_composite(im2, ((SIZE - im2.width) // 2, (SIZE - im2.height) // 2))
    return canvas, 'padded'

def main(tsv, outdir, report_path, sheet_path):
    import os
    os.makedirs(outdir, exist_ok=True)
    report, done = {}, []
    for line in open(tsv, encoding='utf-8'):
        if line.startswith('#') or not line.strip(): continue
        slug, title, color, domains = line.rstrip('\n').split('\t')[:4]
        print(slug)
        best = None
        for domain in domains.split(','):
            for src, url, pref in candidates(domain.strip()):
                try:
                    _, data = get(url)
                    im = load(data)
                except Exception as e:
                    continue
                w, h = im.size
                if min(w, h) < 32 or not (0.6 <= w / h <= 1.7): continue
                # prefer the site's own large icons, then size
                rank = min(w, h) + (300 if src.startswith('site') or src == 'apple-touch-icon' else 0)
                if src == 'google' and min(w, h) <= 16: continue
                if best is None or rank > best[0]:
                    best = (rank, src, url, im)
            if best and best[0] >= 300 + 120: break
        if not best:
            report[slug] = {'ok': False}
            print('  NO ICON'); continue
        tile, mode = compose(best[3])
        tile.convert('RGB').save(os.path.join(outdir, slug + '.png'), optimize=True)
        report[slug] = {'ok': True, 'source': best[1], 'url': best[2], 'size': best[3].size, 'mode': mode}
        done.append((slug, title, tile))
        print('  ok', best[1], best[3].size, mode)
    json.dump(report, open(report_path, 'w'), indent=1)
    # contact sheet for review, tiles clipped to circles like the app shows them
    cols, cell = 8, 150
    rows = (len(done) + cols - 1) // cols
    sheet = Image.new('RGB', (cols * cell, rows * (cell + 10)), (247, 248, 252))
    d = ImageDraw.Draw(sheet)
    mask = Image.new('L', (SIZE, SIZE), 0); ImageDraw.Draw(mask).ellipse((0, 0, SIZE - 1, SIZE - 1), fill=255)
    for i, (slug, title, tile) in enumerate(done):
        x, y = (i % cols) * cell + 11, (i // cols) * (cell + 10) + 4
        sheet.paste(tile.convert('RGB'), (x, y), mask)
        d.text((x, y + SIZE + 2), slug[:20], fill=(30, 30, 30))
    sheet.save(sheet_path)
    print('icons:', len(done), 'missing:', [k for k, v in report.items() if not v['ok']])

if __name__ == '__main__':
    main(*sys.argv[1:5])
