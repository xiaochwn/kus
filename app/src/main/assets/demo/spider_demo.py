"""BoxZero 内置演示 py 爬虫：视频/海报走 App 本地代理，全离线可播。

视频与海报打包在 APK 的 assets/demo 里，App 首次启动拷贝到 filesDir，
本爬虫通过 proxy_base（127.0.0.1 本地代理）取流，手机不联网也能看。
"""

from boxzero.base.spider import Spider

VIDEOS = [
    {'id': 'bunny', 'file': 'bunny.mp4', 'poster': 'p1.jpg',
     'name': 'Big Buck Bunny（演示）', 'remarks': '10秒 · 动画',
     'desc': 'Blender 基金会开放电影 Big Buck Bunny 片段。'},
    {'id': 'jellyfish', 'file': 'jellyfish.mp4', 'poster': 'p2.jpg',
     'name': 'Jellyfish（演示）', 'remarks': '10秒 · 自然',
     'desc': '水母测试片段。'},
    {'id': 'flower', 'file': 'flower.mp4', 'poster': 'p3.jpg',
     'name': 'Flower（演示）', 'remarks': '短片 · 自然',
     'desc': '一朵花的延时摄影。'},
    {'id': 'sintel', 'file': 'sintel_trailer.mp4', 'poster': 'p4.jpg',
     'name': 'Sintel 预告（演示）', 'remarks': '预告 · 奇幻',
     'desc': 'Blender 基金会开放电影 Sintel 官方预告。'},
]


class Spider(Spider):
    def vurl(self, name):
        return self.proxy_base + '?do=demo_video&name=' + name

    def purl(self, name):
        return self.proxy_base + '?do=demo_poster&name=' + name

    def homeContent(self, filter):
        return {'class': [{'type_id': 'all', 'type_name': '全部'}]}

    def homeVideoContent(self):
        return self.result([
            self.vod(v['id'], v['name'], self.purl(v['poster']), v['remarks'])
            for v in VIDEOS
        ])

    def categoryContent(self, tid, pg, filter, extend):
        pg = max(1, int(pg or 1))
        per = 4
        items = VIDEOS[(pg - 1) * per:pg * per]
        total = len(VIDEOS)
        return {
            'list': [
                self.vod(v['id'], v['name'], self.purl(v['poster']), v['remarks'])
                for v in items
            ],
            'page': pg,
            'pagecount': (total + per - 1) // per,
            'limit': per,
            'total': total,
        }

    def detailContent(self, ids):
        vid = ids[0] if ids else ''
        v = next((x for x in VIDEOS if x['id'] == vid), VIDEOS[0])
        eps = '正片$' + self.vurl(v['file'])
        others = [x for x in VIDEOS if x['id'] != v['id']][:3]
        teasers = '#'.join('%s$%s' % (o['name'], self.vurl(o['file'])) for o in others)
        return {'list': [{
            'vod_id': v['id'],
            'vod_name': v['name'],
            'vod_pic': self.purl(v['poster']),
            'vod_remarks': v['remarks'],
            'vod_content': v['desc'],
            'vod_play_from': '高清$$$花絮',
            'vod_play_url': eps + '$$$' + teasers,
        }]}

    def searchContent(self, key, quick, pg='1'):
        key = (key or '').lower()
        hit = [v for v in VIDEOS if key in v['name'].lower() or key in v['id']]
        return self.result([
            self.vod(v['id'], v['name'], self.purl(v['poster']), v['remarks'])
            for v in hit
        ])

    def playerContent(self, flag, id, vipFlags):
        return {'parse': 0, 'url': id, 'header': {}}
