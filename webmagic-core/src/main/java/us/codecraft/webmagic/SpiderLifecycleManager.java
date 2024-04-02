package us.codecraft.webmagic;

public class SpiderLifecycleManager {
    private Spider spider;

    public SpiderLifecycleManager(Spider spider) {
        this.spider = spider;
    }

    public void runAsync() {
        Thread thread = new Thread(spider);
        thread.setDaemon(false);
        thread.start();
    }

    public void stop() {
        if (spider.stat.compareAndSet(Spider.STAT_RUNNING, Spider.STAT_STOPPED)) {
            spider.logger.info("Spider " + spider.getUUID() + " stop success!");
        } else {
            spider.logger.info("Spider " + spider.getUUID() + " stop fail!");
        }
    }

}

