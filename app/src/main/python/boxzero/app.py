"""Bootstrap for .py spiders, called from Java (PySpider).

A py spider source is a .py file whose shape the whole TVBox ecosystem shares:
a module defining ``class Spider`` with methods like::

    def init(self, extend=''): ...
    def homeContent(self, filter): ...            -> dict
    def homeVideoContent(self): ...               -> dict {"list": [vod...]}
    def categoryContent(self, tid, pg, filter, extend): ... -> dict
    def detailContent(self, ids): ...             -> dict
    def searchContent(self, key, quick, pg='1'): ... -> dict
    def playerContent(self, flag, id, vipFlags): ... -> dict {"url":..., "parse":0/1}
    def localProxy(self, params): ...             -> [code, mime, bytes]

``api`` may be an http(s) URL to the .py file, a local file path,
or the .py source code itself (inline). Dict/list results are JSON-encoded
here so Java only ever deals with strings.
"""

import json
import os
import types
import urllib.request


def _read_source(api):
    if api.startswith('http://') or api.startswith('https://'):
        req = urllib.request.Request(api, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req, timeout=20) as resp:
            return resp.read()
    if api and os.path.isfile(api):
        with open(api, 'rb') as f:
            return f.read()
    return (api or '').encode('utf-8')


def _dumps(obj):
    return json.dumps(obj, ensure_ascii=False)


def create(api, site_key, proxy_base):
    """Load the spider module and instantiate its Spider class."""
    src = _read_source(api)
    safe = ''.join(c if c.isalnum() else '_' for c in (site_key or 'x'))
    mod = types.ModuleType('boxzero_dyn_' + safe)
    exec(compile(src, '<spider:%s>' % site_key, 'exec'), mod.__dict__)
    spider = mod.__dict__['Spider']()
    spider.site_key = site_key or ''
    spider.proxy_base = proxy_base or ''
    return spider


def init_spider(spider, ext):
    fn = getattr(spider, 'init', None)
    if fn:
        fn(ext or '')


def _call(spider, name, *args):
    fn = getattr(spider, name, None)
    if fn is None:
        return ''
    res = fn(*args)
    if res is None:
        return ''
    if isinstance(res, bool):
        return 'true' if res else 'false'
    if isinstance(res, (dict, list)):
        return _dumps(res)
    return str(res)


def home(spider, filter):
    return _call(spider, 'homeContent', bool(filter))


def home_video(spider):
    return _call(spider, 'homeVideoContent')


def category(spider, tid, pg, filter, extend_json):
    try:
        extend = json.loads(extend_json or '{}')
    except Exception:
        extend = {}
    return _call(spider, 'categoryContent', tid or '', pg or '1', bool(filter), extend)


def detail(spider, ids_json):
    try:
        ids = json.loads(ids_json or '[]')
    except Exception:
        ids = []
    return _call(spider, 'detailContent', ids)


def search(spider, key, quick, pg):
    return _call(spider, 'searchContent', key or '', bool(quick), pg or '1')


def player(spider, flag, id, vip_json):
    try:
        vip = json.loads(vip_json or '[]')
    except Exception:
        vip = []
    return _call(spider, 'playerContent', flag or '', id or '', vip)


def live(spider, url):
    return _call(spider, 'liveContent', url or '')


def manual_check(spider):
    fn = getattr(spider, 'manualVideoCheck', None)
    return bool(fn()) if fn else False


def is_video(spider, url):
    fn = getattr(spider, 'isVideoFormat', None)
    return bool(fn(url)) if fn else True


def proxy(spider, param_json):
    """localProxy bridge. Returns [code, mime, bytes] for Java."""
    try:
        params = json.loads(param_json or '{}')
    except Exception:
        params = {}
    fn = getattr(spider, 'localProxy', None)
    if fn is None:
        return [404, 'text/plain', b'']
    try:
        res = fn(params)
        code = int(res[0])
        mime = str(res[1])
        data = res[2]
        if isinstance(data, str):
            data = data.encode('utf-8')
        return [code, mime, bytes(data or b'')]
    except Exception:
        return [500, 'text/plain', b'proxy error']


def destroy_spider(spider):
    fn = getattr(spider, 'destroy', None)
    if fn:
        try:
            fn()
        except Exception:
            pass
