package us.codecraft.webmagic;

import java.util.concurrent.TimeUnit;

public class UrlManager {
    private Spider spider;

    public UrlManager(Spider spider) {
        this.spider = spider;
    }

    /**
     *
     * @return isInterrupted
     */
    public boolean waitNewUrl() {
        // now there may not be any thread live
        spider.newUrlLock.lock();
        try {
            //double check，unnecessary, unless very fast concurrent
            if (spider.threadPool.getThreadAlive() == 0) {
                return false;
            }
            //wait for amount of time
            spider.newUrlCondition.await(spider.emptySleepTime, TimeUnit.MILLISECONDS);
            return false;
        } catch (InterruptedException e) {
            // logger.warn("waitNewUrl - interrupted, error {}", e);
            return true;
        } finally {
            spider.newUrlLock.unlock();
        }
    }

    void signalNewUrl() {
        try {
            spider.newUrlLock.lock();
            spider.newUrlCondition.signalAll();
        } finally {
            spider.newUrlLock.unlock();
        }
    }

    /**
     * Add urls to crawl. <br>
     *
     * @param urls urls
     * @return this
     */
    public Spider addUrl(String... urls) {
        for (String url : urls) {
            spider.addRequest(new Request(url));
        }
        signalNewUrl();
        return spider;
    }

}