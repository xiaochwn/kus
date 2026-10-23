"""BoxZero 演示 py 爬虫：静态演示数据，验证整条链路（加载/分类/详情/搜索/播放）。

视频与海报都托管在本仓库 demo/ 目录，经 jsDelivr CDN 分发，国内可直接访问。

在 BoxZero 里这样写爬虫即可：
    from boxzero.base.spider import Spider
    class Spider(Spider): ...
"""

from boxzero.base.spider import Spider

CDN = 'https://cdn.jsdelivr.net/gh/xiaochwn/kus@main/demo'

VIDEOS = [
    {'id': 'bunny', 'name': 'Big Buck Bunny（演示）',
     'pic': CDN + '/posters/p1.jpg', 'remarks': '10秒 · 动画',
     'url': CDN + '/videos/bunny.mp4',
     'desc': 'Blender 基金会开放电影 Big Buck Bunny 片段。'},
    {'id': 'jellyfish', 'name': 'Jellyfish（演示）',
     'pic': CDN + '/posters/p2.jpg', 'remarks': '10秒 · 自然',
     'url': CDN + '/videos/jellyfish.mp4',
     'desc': '水母 4K 测试片段。'},
    {'id': 'flower', 'name': 'Flower（演示）',
     'pic': CDN + '/posters/p3.jpg', 'remarks': '短片 · 自然',
     'url': CDN + '/videos/flower.mp4',
     'desc': 'MDN 示例视频：一朵花的延时摄影。'},
    {'id': 'sintel', 'name': 'Sintel 预告（演示）',
     'pic': CDN + '/posters/p4.jpg', 'remarks': '预告 · 奇幻',
     'url': CDN + '/videos/sintel_trailer.mp4',
     'desc': 'Blender 基金会开放电影 Sintel 官方预告。'},
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
        return {'parse': 0, 'url': id, 'header': {}}
