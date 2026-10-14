"""Read published Spanish catalogs; retain explicit paths, never guess image names."""
import concurrent.futures, json, re, subprocess
from pathlib import Path
BASE = 'https://tcgpocket.centropkmn.com/es_ES/'
def catalog(code):
    page = subprocess.check_output(['curl', '-fLsS', '--retry', '2', '--max-time', '30', BASE + code.lower()], text=True)
    for script in re.findall(r'<script[^>]*>(.*?)</script>', page):
        try:
            data = json.loads(json.loads(script).get('body', '{}'))
        except (ValueError, AttributeError):
            continue
        if data.get('language', {}).get('code') == 'es_ES' and 'cards' in data and 'expansion' in data:
            return code, data['cards']
    raise ValueError('No Spanish catalog: ' + code)
if __name__ == '__main__':
    manifest_page = subprocess.check_output(['curl', '-fLsS', '--max-time', '30', BASE], text=True)
    manifest = next(data for script in re.findall(r'<script[^>]*>(.*?)</script>', manifest_page) if script.startswith('{\"status\"') for data in [json.loads(json.loads(script).get('body', '{}'))] if 'expansions' in data)
    index = {}
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        for code, cards in pool.map(catalog, [e['code'] for e in manifest['expansions']]):
            for card in cards:
                path = card['image']
                if '/es_ES/' not in path or not path.startswith('/cards/'):
                    raise ValueError('Unexpected language/path')
                index[f'{code.upper()}-{card["number"]:03}'] = path
            print(code, len(cards), flush=True)
    target = Path('app/src/main/assets/pocket-spanish-image-index.json')
    target.write_text(json.dumps({'source': 'Centro Pokémon', 'verifiedCatalogDate': '2026-10-11', 'images': index}, ensure_ascii=False, separators=(',', ':')))
    print('INDEXED', len(index))
