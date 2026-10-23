"""BoxZero 演示 py 爬虫：纯静态数据，验证整条链路（加载/分类/详情/搜索/播放）。

在 BoxZero 里这样写爬虫即可：
    from boxzero.base.spider import Spider
    class Spider(Spider): ...
"""

from boxzero.base.spider import Spider

BUCKET = 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample'

VIDEOS = [
    {'id': 'bbb', 'name': 'Big Buck Bunny', 'pic': 'https://picsum.photos/seed/bbb/300/400',
     'remarks': '10分钟 · 动画', 'url': BUCKET + '/BigBuckBunny.mp4',
     'desc': '一只大兔子对三只小啮齿动物的复仇喜剧。Blender 基金会开放电影。'},
    {'id': 'ed', 'name': 'Elephants Dream', 'pic': 'https://picsum.photos/seed/ed/300/400',
     'remarks': '11分钟 · 科幻', 'url': BUCKET + '/ElephantsDream.mp4',
     'desc': '两个男人在一台无限大的机器里穿行，探讨现实与幻觉。首部开放电影。'},
    {'id': 'sintel', 'name': 'Sintel', 'pic': 'https://picsum.photos/seed/sintel/300/400',
     'remarks': '15分钟 · 奇幻', 'url': BUCKET + '/Sintel.mp4',
     'desc': '女孩 Sintel 寻找她的小火龙，穿越冰雪荒原的冒险。'},
    {'id': 'tos', 'name': 'Tears of Steel', 'pic': 'https://picsum.photos/seed/tos/300/400',
     'remarks': '12分钟 · 科幻', 'url': BUCKET + '/TearsOfSteel.mp4',
     'desc': '阿姆斯特丹，机器人与人类的未来战争。Blender 基金会第四部开放电影。'},
    {'id': 'blazes', 'name': 'For Bigger Blazes', 'pic': 'https://picsum.photos/seed/blazes/300/400',
     'remarks': '15秒 · 广告', 'url': BUCKET + '/ForBiggerBlazes.mp4',
     'desc': 'Google Chromecast 演示短片。'},
    {'id': 'escapes', 'name': 'For Bigger Escapes', 'pic': 'https://picsum.photos/seed/escapes/300/400',
     'remarks': '15秒 · 广告', 'url': BUCKET + '/ForBiggerEscapes.mp4',
     'desc': 'Google Chromecast 演示短片。'},
]


class Spider(Spider):
    def homeContent(self, filter):
        return {'class': [{'type_id': 'all', 'type_name': '全部'}]}

    def homeVideoContent(self):
        return self.result([self.vod(v['id'], v['name'], v['pic'], v['remarks']) for v in VIDEOS])

    def categoryContent(self, tid, pg, filter, extend):
        pg = max(1, int(pg or 1))
        per = 4
        items = VIDEOS[(pg - 1) * per:pg * per]
        total = len(VIDEOS)
        return {
            'list': [self.vod(v['id'], v['name'], v['pic'], v['remarks']) for v in items],
            'page': pg,
            'pagecount': (total + per - 1) // per,
            'limit': per,
            'total': total,
        }

    def detailContent(self, ids):
        vid = ids[0] if ids else ''
        v = next((x for x in VIDEOS if x['id'] == vid), VIDEOS[0])
        eps = '正片$' + v['url']
        others = [x for x in VIDEOS if x['id'] != v['id']][:3]
        teasers = '#'.join('%s$%s' % (o['name'], o['url']) for o in others)
        return {'list': [{
            'vod_id': v['id'],
            'vod_name': v['name'],
            'vod_pic': v['pic'],
            'vod_remarks': v['remarks'],
            'vod_content': v['desc'],
            'vod_play_from': '高清$$$花絮',
            'vod_play_url': eps + '$$$' + teasers,
        }]}

    def searchContent(self, key, quick, pg='1'):
        key = (key or '').lower()
        hit = [v for v in VIDEOS if key in v['name'].lower() or key in v['id']]
        return self.result([self.vod(v['id'], v['name'], v['pic'], v['remarks']) for v in hit])

    def playerContent(self, flag, id, vipFlags):
        return {'parse': 0, 'url': id, 'header': {'Referer': 'https://commondatastorage.googleapis.com/'}}
