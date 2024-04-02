package us.codecraft.webmagic;

import java.util.concurrent.ExecutorService;

public class ThreadManager {
    private Spider spider;

    public ThreadManager(Spider spider) {
        this.spider = spider;
    }

    /**
     * start with more than one threads
     *
     * @param threadNum threadNum
     * @return this
     */
    public Spider thread(int threadNum) {
        spider.checkIfRunning();
        spider.threadNum = threadNum;
        if (threadNum <= 0) {
            throw new IllegalArgumentException("threadNum should be more than one!");
        }
        return spider;
    }

    /**
     * start with more than one threads
     *
     * @param executorService executorService to run the spider
     * @param threadNum threadNum
     * @return this
     */
    public Spider thread(ExecutorService executorService, int threadNum) {
        spider.checkIfRunning();
        spider.threadNum = threadNum;
        if (threadNum <= 0) {
            throw new IllegalArgumentException("threadNum should be more than one!");
        }
        spider.executorService = executorService;
        return spider;
    }

    /**
     * Get thread count which is running
     *
     * @return thread count which is running
     * @since 0.4.1
     */
    public int getThreadAlive() {
        if (spider.threadPool == null) {
            return 0;
        }
        return spider.threadPool.getThreadAlive();
    }
}

