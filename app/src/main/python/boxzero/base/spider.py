"""Optional helper base class for py spiders.

Usage in a spider file::

    from boxzero.base.spider import Spider

    class Spider(Spider):
        def init(self, extend=''):
            self.host = 'https://example.com'
        def homeVideoContent(self):
            ...

Only override what you need; everything else safely returns empty.
``proxy_url(params)`` builds a local-proxy URL that routes through the
app's ProxyServer back into this spider's ``localProxy``.
"""

import re

import requests
from urllib.parse import urlencode


class Spider:
    def __init__(self):
        self.site_key = ''
        self.proxy_base = ''
        self._session = requests.Session()
        self._session.headers.update({
            'User-Agent': 'Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36'
        })

    # ---------------- override these ----------------
    def init(self, extend=''):
        pass

    def homeContent(self, filter):
        return {}

    def homeVideoContent(self):
        return {}

    def categoryContent(self, tid, pg, filter, extend):
        return {}

    def detailContent(self, ids):
        return {}

    def searchContent(self, key, quick, pg='1'):
        return {}

    def playerContent(self, flag, id, vipFlags):
        return {}

    def liveContent(self, url):
        return {}

    def localProxy(self, params):
        return [404, 'text/plain', b'']

    def isVideoFormat(self, url):
        return True

    def manualVideoCheck(self):
        return False

    def destroy(self):
        try:
            self._session.close()
        except Exception:
            pass

    # ---------------- helpers ----------------
    def fetch(self, url, **kwargs):
        kwargs.setdefault('timeout', 15)
        r = self._session.get(url, **kwargs)
        r.encoding = r.apparent_encoding or 'utf-8'
        return r

    def post(self, url, **kwargs):
        kwargs.setdefault('timeout', 15)
        r = self._session.post(url, **kwargs)
        r.encoding = r.apparent_encoding or 'utf-8'
        return r

    def html(self, content):
        from lxml import etree
        return etree.HTML(content)

    def clean(self, s):
        return re.sub(r'<.*?>', '', s or '')

    def proxy_url(self, params=None):
        q = {'do': 'py', 'siteKey': self.site_key}
        q.update(params or {})
        return self.proxy_base + '?' + urlencode(q)

    @staticmethod
    def vod(vod_id, vod_name, vod_pic='', vod_remarks=''):
        return {
            'vod_id': vod_id,
            'vod_name': vod_name,
            'vod_pic': vod_pic,
            'vod_remarks': vod_remarks,
        }

    @staticmethod
    def result(items, pg=1, pagecount=1, limit=20, total=0):
        return {
            'list': items,
            'page': pg,
            'pagecount': pagecount,
            'limit': limit,
            'total': total or len(items),
        }
