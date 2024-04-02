package us.codecraft.webmagic;

import java.util.Date;

import us.codecraft.webmagic.downloader.HttpClientDownloader;
import us.codecraft.webmagic.pipeline.ConsolePipeline;
import us.codecraft.webmagic.processor.PageProcessor;
import us.codecraft.webmagic.thread.CountableThreadPool;

public class InitManager {

    private Spider spider;

    public InitManager(Spider spider) {
        this.spider = spider;
    }

    protected void initComponent() {
        if (spider.downloader == null) {
            spider.downloader = new HttpClientDownloader();
        }
        if (spider.pipelines.isEmpty()) {
            spider.pipelines.add(new ConsolePipeline());
        }
        spider.downloader.setThread(spider.threadNum);
        if (spider.threadPool == null || spider.threadPool.isShutdown()) {
            if (spider.executorService != null && !spider.executorService.isShutdown()) {
                spider.threadPool = new CountableThreadPool(spider.threadNum, spider.executorService);
            } else {
                spider.threadPool = new CountableThreadPool(spider.threadNum);
            }
        }
        if (spider.startRequests != null) {
            for (Request request : spider.startRequests) {
                spider.addRequest(request);
            }
            spider.startRequests.clear();
        }
        spider.startTime = new Date();
    }
}

