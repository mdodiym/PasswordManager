"""Second pass for providers that tools/fetch_logos.py found no icon for: use the provider's app icon from the
Malaysian App Store (iTunes Search API). Only accepts a result whose app or seller name matches the provider.
Usage: python3 tools/fetch_appstore_icons.py assets/malaysia.tsv assets/logos tools/appstore-report.json tools/appstore-sheet.png"""
import io, json, os, re, sys, urllib.parse, urllib.request
from PIL import Image, ImageDraw
sys.path.insert(0, os.path.dirname(__file__))
from fetch_logos import compose, get, SIZE

def words(s):
    return [w for w in re.split(r'[^a-z0-9]+', s.lower()) if len(w) >= 3 and w not in ('bank', 'malaysia', 'berhad', 'the', 'and', 'sdn', 'bhd')]

def main(tsv, outdir, report_path, sheet_path):
    have = {f[:-4] for f in os.listdir(outdir)}
    report, done = {}, []
    for line in open(tsv, encoding='utf-8'):
        if line.startswith('#') or not line.strip(): continue
        slug, title, color, domains, aliases = line.rstrip('\n').split('\t')[:5]
        if slug in have: continue
        keys = set(words(title))
        for a in aliases.split('|'): keys.update(words(a))
        terms = [title] + [a for a in aliases.split('|') if a][:2]
        pick = None
        for term in terms:
            q = urllib.parse.urlencode({'term': term, 'country': 'my', 'entity': 'software', 'limit': 8})
            try:
                _, body = get('https://itunes.apple.com/search?' + q)
                results = json.loads(body.decode('utf-8'))['results']
            except Exception as e:
                print(slug, 'search failed', e); continue
            for r in results:
                name = (r.get('trackName', '') + ' ' + r.get('sellerName', '')).lower()
                if keys and any(k in name for k in keys):
                    pick = r; break
            if pick: break
        if not pick:
            report[slug] = {'ok': False}; print(slug, 'no match'); continue
        url = pick.get('artworkUrl512') or pick.get('artworkUrl100')
        try:
            _, data = get(url)
            im = Image.open(io.BytesIO(data)).convert('RGBA')
        except Exception as e:
            report[slug] = {'ok': False, 'error': str(e)}; continue
        tile, mode = compose(im)
        tile.convert('RGB').save(os.path.join(outdir, slug + '.png'), optimize=True)
        report[slug] = {'ok': True, 'app': pick.get('trackName'), 'seller': pick.get('sellerName'), 'url': url, 'mode': mode}
        done.append((slug, pick.get('trackName', ''), tile))
        print(slug, '->', pick.get('trackName'), '/', pick.get('sellerName'))
    json.dump(report, open(report_path, 'w'), indent=1)
    cols, cell = 6, 200
    rows = max(1, (len(done) + cols - 1) // cols)
    sheet = Image.new('RGB', (cols * cell, rows * (cell + 10)), (247, 248, 252))
    d = ImageDraw.Draw(sheet)
    mask = Image.new('L', (SIZE, SIZE), 0); ImageDraw.Draw(mask).ellipse((0, 0, SIZE - 1, SIZE - 1), fill=255)
    for i, (slug, app, tile) in enumerate(done):
        x, y = (i % cols) * cell + 36, (i // cols) * (cell + 10) + 4
        sheet.paste(tile.convert('RGB'), (x, y), mask)
        d.text((x - 30, y + SIZE + 4), (slug + ': ' + app)[:34], fill=(30, 30, 30))
    sheet.save(sheet_path)

if __name__ == '__main__':
    main(*sys.argv[1:5])
