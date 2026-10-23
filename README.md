# BoxZero

一个从零写的 TVBox 风格影视壳子（Android）。没有 fork 任何现有 App 项目：
所有代码都是新写的，只依赖公开库（ExoPlayer / OkHttp / Gson / Glide / Chaquopy）。

## 能干什么（v0.1）

- 填一个 TVBox 格式的接口地址（JSON），加载 `sites` / `parses`
- 站点切换、首页推荐、分类、详情、选集、搜索、播放
- **py 爬虫**：`api` 里带 `.py` 的站点，用内置 Chaquopy Python 解释器在手机上直接跑
  - `api` 可以是 `.py` 文件的 http(s) 地址、本地路径，或直接内联的 py 源码
  - 自带 `boxzero.base.spider.Spider` 基类（`from boxzero.base.spider import Spider`），只重写需要的方法即可
- **jar 爬虫**：`api` 为 `csp_Xxx` 的站点，从接口 `spider` 字段下载 jar，用 DexClassLoader 加载
  - `com.github.catvod.crawler.Spider` 按生态二进制接口原样实现，市面上的 jar 爬虫可直接加载
- 本地代理服务（`http://127.0.0.1:端口?do=py&siteKey=…`）：py/jar 爬虫的 `localProxy` 走这里，播放器直接播代理地址
- ExoPlayer 播放，支持爬虫返回的自定义 header；`parse:1` 的播放地址用接口 `parses` 做尽力解析

## 目录结构

```
app/src/main/java/com/boxzero/app/
  App.java                  # Application：启动 Python、代理服务
  core/
    BoxConfig.java          # 接口 JSON 加载（sites / parses / spider jar）
    Site.java               # 站点 bean
    Vod.java                # 影片 bean + vod_play_from/url 选集解析
    Prefs.java              # 接口地址、当前站点持久化
    SpiderLoader.java        # 爬虫分发：.py -> PySpider，csp_ -> JarSpider
    PySpider.java           # Chaquopy 桥接
    JarSpider.java          # DexClassLoader 加载 jar 爬虫
    ProxyServer.java        # 127.0.0.1 本地代理（localProxy 落点）
  ui/
    MainActivity.java       # 站点/搜索/海报墙
    DetailActivity.java     # 详情 + 播放源 + 选集
    PlayerActivity.java     # ExoPlayer 播放
    SettingsActivity.java   # 填接口地址
    VodAdapter.java
app/src/main/python/boxzero/
  app.py                    # py 爬虫加载器：读源码 -> exec -> 调方法 -> JSON 编解码
  base/spider.py            # py 爬虫可选基类（fetch/post/html/proxy_url…）
```

## py 爬虫协议

py 文件定义 `class Spider`，方法签名与生态一致，返回 dict（这里自动转 JSON）：

```python
from boxzero.base.spider import Spider

class Spider(Spider):
    def init(self, extend=''): ...
    def homeVideoContent(self): return self.result([...])
    def categoryContent(self, tid, pg, filter, extend): ...
    def detailContent(self, ids): ...
    def searchContent(self, key, quick, pg='1'): ...
    def playerContent(self, flag, id, vipFlags):
        return {'parse': 0, 'url': 'https://…/x.m3u8'}
    def localProxy(self, params): return [200, 'video/mp2t', data]
```

## 构建

本机没有 Android toolchain，走 GitHub Actions（`.github/workflows/build.yml`）：

1. 在 GitHub 新建空仓库，把本目录推上去
2. push 到 main 自动构建，产物 `BoxZero-apk`（debug 包，30 天有效）

## 已有功能（v0.52）

- 首页：轮播 banner（自动滚动）、全部分类胶囊、推荐横滑海报墙、顶部搜索栏、源切换
- 分类页（网格 + 翻页）、搜索页、详情页（选集 + 收藏星标）、播放页（ExoPlayer）
- 解析：直连、`parses` 拼接/JSON 解析、WebView 嗅探（`parse:1` 兜底）
- 播放历史（自动记录，点击续播）/ 收藏 / 直播（txt / m3u / JSON）/ 我的（接口设置）
- 毛玻璃底部导航：首页 / 收藏 / 历史 / 直播 / 我的
- py spider + jar spider（csp_*）、本地代理（localProxy）

## TODO（v1 之后）

- XPath 站点（type 0）和采集站（type 1）
- 选集记忆（看到第几集）
- TMDB 刮削补海报简介
