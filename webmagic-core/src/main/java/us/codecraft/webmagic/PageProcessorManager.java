package us.codecraft.webmagic;

import us.codecraft.webmagic.processor.PageProcessor;
import org.apache.commons.lang3.SerializationUtils;
import us.codecraft.webmagic.pipeline.Pipeline;

public class PageProcessorManager {
    private PageProcessor pageProcessor;
    private Spider spider;

    public PageProcessorManager(PageProcessor pageProcessor, Spider spider) {
        this.pageProcessor = pageProcessor;
        this.spider = spider;
    }

    public void processRequest(Request request) {
        Page page;
        if (null != request.getDownloader()){
            page = request.getDownloader().download(request, spider);
        }else {
            page = spider.downloader.download(request, spider);
        }
        if (page.isDownloadSuccess()) {
            onDownloadSuccess(request, page);
        } else {
            onDownloaderFail(request);
        }
    }

    private void onDownloadSuccess(Request request, Page page) {
        if (spider.getSite().getAcceptStatCode().contains(page.getStatusCode())) {
            pageProcessor.process(page);
            spider.extractAndAddRequests(page, spider.isSpawnUrl());
            if (!page.getResultItems().isSkip()) {
                for (Pipeline pipeline : spider.pipelines) {
                    pipeline.process(page.getResultItems(), spider);
                }
            }
        } else {
            spider.logger.info("page status code error, page {} , code: {}", request.getUrl(), page.getStatusCode());
        }
        spider.sleep(spider.getSite().getSleepTime());
    }

    private void onDownloaderFail(Request request) {
        if (spider.getSite().getCycleRetryTimes() == 0) {
            spider.sleep(spider.getSite().getSleepTime());
        } else {
            doCycleRetry(request);
        }
    }

    public void doCycleRetry(Request request) {
        Object cycleTriedTimesObject = request.getExtra(Request.CYCLE_TRIED_TIMES);
        if (cycleTriedTimesObject == null) {
            spider.addRequest(SerializationUtils.clone(request).setPriority(0).putExtra(Request.CYCLE_TRIED_TIMES, 1));
        } else {
            int cycleTriedTimes = (Integer) cycleTriedTimesObject;
            cycleTriedTimes++;
            if (cycleTriedTimes < spider.site.getCycleRetryTimes()) {
                spider.addRequest(SerializationUtils.clone(request).setPriority(0).putExtra(Request.CYCLE_TRIED_TIMES, cycleTriedTimes));
            }
        }
        spider.sleep(spider.site.getRetrySleepTime());
    }

}
