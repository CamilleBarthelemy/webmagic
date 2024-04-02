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
        if (spider.stat.compareAndSet(spider.STAT_RUNNING, spider.STAT_STOPPED)) {
            spider.logger.info("Spider " + spider.getUUID() + " stop success!");
        } else {
            spider.logger.info("Spider " + spider.getUUID() + " stop fail!");
        }
    }

}

