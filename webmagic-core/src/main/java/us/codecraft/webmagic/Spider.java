package us.codecraft.webmagic;


import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import us.codecraft.webmagic.downloader.Downloader;
import us.codecraft.webmagic.pipeline.CollectorPipeline;
import us.codecraft.webmagic.pipeline.Pipeline;
import us.codecraft.webmagic.pipeline.ResultItemsCollectorPipeline;
import us.codecraft.webmagic.processor.PageProcessor;
import us.codecraft.webmagic.scheduler.QueueScheduler;
import us.codecraft.webmagic.scheduler.Scheduler;
import us.codecraft.webmagic.thread.CountableThreadPool;
import us.codecraft.webmagic.utils.UrlUtils;
import us.codecraft.webmagic.utils.WMCollections;

/**
 * Entrance of a crawler.<br>
 * A spider contains four modules: Downloader, Scheduler, PageProcessor and
 * Pipeline.<br>
 * Every module is a field of Spider. <br>
 * The modules are defined in interface. <br>
 * You can customize a spider with various implementations of them. <br>
 * Examples: <br>
 * <br>
 * A simple crawler: <br>
 * Spider.create(new SimplePageProcessor("http://my.oschina.net/",
 * "http://my.oschina.net/*blog/*")).run();<br>
 * <br>
 * Store results to files by FilePipeline: <br>
 * Spider.create(new SimplePageProcessor("http://my.oschina.net/",
 * "http://my.oschina.net/*blog/*")) <br>
 * .pipeline(new FilePipeline("/data/temp/webmagic/")).run(); <br>
 * <br>
 * Use FileCacheQueueScheduler to store urls and cursor in files, so that a
 * Spider can resume the status when shutdown. <br>
 * Spider.create(new SimplePageProcessor("http://my.oschina.net/",
 * "http://my.oschina.net/*blog/*")) <br>
 * .scheduler(new FileCacheQueueScheduler("/data/temp/webmagic/cache/")).run(); <br>
 *
 * @author code4crafter@gmail.com <br>
 * @see Downloader
 * @see Scheduler
 * @see PageProcessor
 * @see Pipeline
 * @since 0.1.0
 */
public class Spider implements Runnable, Task {

    public Date startTime;

    public ReentrantLock newUrlLock = new ReentrantLock();

    public Condition newUrlCondition = newUrlLock.newCondition();

    protected Downloader downloader;

    protected List<Pipeline> pipelines = new ArrayList<>();

    protected PageProcessor pageProcessor;

    protected List<Request> startRequests;

    protected Site site;

    protected String uuid;

    protected Scheduler scheduler = new QueueScheduler();

    protected Logger logger = LoggerFactory.getLogger(getClass());

    protected CountableThreadPool threadPool;

    protected ExecutorService executorService;

    protected int threadNum = 1;

    protected AtomicInteger stat = new AtomicInteger(STAT_INIT);

    protected boolean exitWhenComplete = true;

    protected boolean spawnUrl = true;

    protected boolean destroyWhenExit = true;

    private UrlManager urlManager;

    private ThreadManager threadManager;

    private InitManager initManager;

    private PageProcessorManager pageProcessorManager;

    private SpiderLifecycleManager lifecycleManager;

    private List<SpiderListener> spiderListeners;

    private final AtomicLong pageCount = new AtomicLong(0);

    public long emptySleepTime = 30000;

    protected static final int STAT_INIT = 0;

    protected static final int STAT_RUNNING = 1;

    protected static final int STAT_STOPPED = 2;

    /**
     * create a spider with pageProcessor.
     *
     * @param pageProcessor pageProcessor
     * @return new spider
     * @see PageProcessor
     */
    public static Spider create(PageProcessor pageProcessor) {
        return new Spider(pageProcessor);
    }

    /**
     * create a spider with pageProcessor.
     *
     * @param pageProcessor pageProcessor
     */
    public Spider(PageProcessor pageProcessor) {
        this.pageProcessor = pageProcessor;
        this.site = pageProcessor.getSite();
        this.lifecycleManager = new SpiderLifecycleManager(this);
        this.pageProcessorManager = new PageProcessorManager(pageProcessor, this);
        this.initManager = new InitManager(this);
        this.threadManager = new ThreadManager(this);
        this.urlManager = new UrlManager(this);
    }

    /**
     * Set startUrls of Spider.<br>
     * Prior to startUrls of Site.
     *
     * @param startUrls startUrls
     * @return this
     */
    public Spider startUrls(List<String> startUrls) {
        RequestUtils.startWithUrls(startUrls, this, spawnUrl, scheduler, true);
        return this;
    }

    /**
     * Set startUrls of Spider.<br>
     * Prior to startUrls of Site.
     *
     * @param startRequests startRequests
     * @return this
     */
    public Spider startRequest(List<Request> startRequests) {
        this.startRequests = startRequests;
        return this;
    }

    /**
     * Set an uuid for spider.<br>
     * Default uuid is domain of site.<br>
     *
     * @param uuid uuid
     * @return this
     */
    public Spider setUUID(String uuid) {
        this.uuid = uuid;
        return this;
    }

    /**
     * add a pipeline for Spider
     *
     * @param pipeline pipeline
     * @return this
     * @see Pipeline
     * @since 0.2.1
     */
    public Spider addPipeline(Pipeline pipeline) {
        checkIfRunning();
        this.pipelines.add(pipeline);
        return this;
    }

    /**
     * clear the pipelines set
     *
     * @return this
     */
    public Spider clearPipeline() {
        pipelines = new ArrayList<>();
        return this;
    }

    /**
     * set the downloader of spider
     *
     * @param downloader downloader
     * @return this
     * @see Downloader
     */
    public Spider setDownloader(Downloader downloader) {
        checkIfRunning();
        this.downloader = downloader;
        return this;
    }

    protected void initComponent() {
        this.initManager.initComponent();
    }

    @Override
    public void run() {
        checkRunningStat();
        initComponent();
        logger.info("Spider {} started!", getUUID());
        // interrupt won't be necessarily detected
        while (!Thread.currentThread().isInterrupted() && stat.get() == STAT_RUNNING) {
            Request poll = scheduler.poll(this);
            if (poll == null) {
                if (threadPool.getThreadAlive() == 0) {
                    //no alive thread anymore , try again
                    poll = scheduler.poll(this);
                    if (poll == null) {
                        if (exitWhenComplete) {
                            break;
                        } else {
                            // wait
                            try {
                                Thread.sleep(emptySleepTime);
                                continue;
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                } else {
                    // wait until new url added，
                    if (waitNewUrl()) {
						//if interrupted
                        break;
					}
                    continue;
                }
            }
            final Request request = poll;
            //this may swallow the interruption
            threadPool.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        pageProcessorManager.processRequest(request);
                        SuccessHandler.onSuccess(request, spiderListeners);
                    } catch (Exception e) {
                        ErrorHandler.onError(request, e, spiderListeners);
                        logger.error("process request " + request + " error", e);
                    } finally {
                        pageCount.incrementAndGet();
                        signalNewUrl();
                    }
                }
            });
        }
        stat.set(STAT_STOPPED);
        // release some resources
        if (destroyWhenExit) {
            close();
        }
        logger.info("Spider {} closed! {} pages downloaded.", getUUID(), pageCount.get());
    }

    private void checkRunningStat() {
        while (true) {
            int statNow = stat.get();
            if (statNow == STAT_RUNNING) {
                throw new IllegalStateException("Spider is already running!");
            }
            if (stat.compareAndSet(statNow, STAT_RUNNING)) {
                break;
            }
        }
    }

    public void close() {
        destroyEach(downloader);
        destroyEach(pageProcessor);
        destroyEach(scheduler);
        for (Pipeline pipeline : pipelines) {
            destroyEach(pipeline);
        }
        threadPool.shutdown();
    }

    private void destroyEach(Object object) {
        if (object instanceof Closeable) {
            try {
                ((Closeable) object).close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Process specific urls without url discovering.
     *
     * @param urls urls to process
     */
    public void test(String... urls) {
        initComponent();
        if (urls.length > 0) {
            for (String url : urls) {
                this.pageProcessorManager.processRequest(new Request(url));
            }
        }
    }

    protected void sleep(int time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            logger.error("Thread interrupted when sleep",e);
            Thread.currentThread().interrupt();
        }
    }

    protected void extractAndAddRequests(Page page, boolean spawnUrl) {
        RequestUtils.extractAndAddRequestsFromPage(page, this, spawnUrl);
    }

    private void addRequest(Request request) {
        if (site.getDomain() == null && request != null && request.getUrl() != null) {
            site.setDomain(UrlUtils.getDomain(request.getUrl()));
        }
        scheduler.push(request, this);
    }

    protected void checkIfRunning() {
        if (stat.get() == STAT_RUNNING) {
            throw new IllegalStateException("Spider is already running!");
        }
    }

    public void runAsync() {
        this.lifecycleManager.runAsync();
    }

    /**
     * Add urls to crawl. <br>
     *
     * @param urls urls
     * @return this
     */
    public UrlManager addUrl(String... urls) {
        return this.urlManager = new UrlManager(this);
    }

    /**
     * Download urls synchronizing.
     *
     * @param urls urls
     * @param <T> type of process result
     * @return list downloaded
     */
    public <T> List<T> getAll(Collection<String> urls) {
        destroyWhenExit = false;
        spawnUrl = false;
        if (startRequests!=null){
            startRequests.clear();
        }
        for (Request request : UrlUtils.convertToRequests(urls)) {
            addRequest(request);
        }
        CollectorPipeline collectorPipeline = getCollectorPipeline();
        pipelines.add(collectorPipeline);
        run();
        spawnUrl = true;
        destroyWhenExit = true;
        return collectorPipeline.getCollected();
    }

    protected CollectorPipeline getCollectorPipeline() {
        return new ResultItemsCollectorPipeline();
    }

    public <T> T get(String url) {
        List<String> urls = WMCollections.newArrayList(url);
        List<T> resultItemses = getAll(urls);
        if (resultItemses != null && resultItemses.size() > 0) {
            return resultItemses.get(0);
        } else {
            return null;
        }
    }

    /**
     * Add urls with information to crawl.<br>
     *
     * @param requests requests
     * @return this
     */
    public Spider addRequest(Request... requests) {
        for (Request request : requests) {
            addRequest(request);
        }
        signalNewUrl();
        return this;
    }

    /**
     *
     * @return isInterrupted
     */
    private boolean waitNewUrl() {
        return this.urlManager.waitNewUrl();
    }

    public void signalNewUrl() {
        this.urlManager.signalNewUrl();
    }

    public void stop() {
        this.lifecycleManager.stop();
    }

    /**
     * start with more than one threads
     *
     * @param threadNum threadNum
     * @return this
     */
    public Spider thread(int threadNum) {
        return this.threadManager.thread(threadNum);
    }

    /**
     * start with more than one threads
     *
     * @param executorService executorService to run the spider
     * @param threadNum threadNum
     * @return this
     */
    public Spider thread(ExecutorService executorService, int threadNum) {
        return this.threadManager.thread(executorService, threadNum);
    }

    public boolean isExitWhenComplete() {
        return exitWhenComplete;
    }

    /**
     * Exit when complete. <br>
     * True: exit when all url of the site is downloaded. <br>
     * False: not exit until call stop() manually.<br>
     *
     * @param exitWhenComplete exitWhenComplete
     * @return this
     */
    public Spider setExitWhenComplete(boolean exitWhenComplete) {
        this.exitWhenComplete = exitWhenComplete;
        return this;
    }

    public boolean isSpawnUrl() {
        return spawnUrl;
    }

    /**
     * Get page count downloaded by spider.
     *
     * @return total downloaded page count
     * @since 0.4.1
     */
    public long getPageCount() {
        return pageCount.get();
    }

    /**
     * Get running status by spider.
     *
     * @return running status
     * @see Status
     * @since 0.4.1
     */
    public Status getStatus() {
        return Status.fromValue(stat.get());
    }

    /**
     * Get thread count which is running
     *
     * @return thread count which is running
     * @since 0.4.1
     */
    public int getThreadAlive() {
        return this.threadManager.getThreadAlive();
    }

    /**
     * Whether add urls extracted to download.<br>
     * Add urls to download when it is true, and just download seed urls when it is false. <br>
     * DO NOT set it unless you know what it means!
     *
     * @param spawnUrl spawnUrl
     * @return this
     * @since 0.4.0
     */
    public Spider setSpawnUrl(boolean spawnUrl) {
        this.spawnUrl = spawnUrl;
        return this;
    }

    @Override
    public String getUUID() {
        if (uuid != null) {
            return uuid;
        }
        if (site != null) {
            return site.getDomain();
        }
        uuid = UUID.randomUUID().toString();
        return uuid;
    }

    public Spider setExecutorService(ExecutorService executorService) {
        checkIfRunning();
        this.executorService = executorService;
        return this;
    }

    @Override
    public Site getSite() {
        return site;
    }

    public List<SpiderListener> getSpiderListeners() {
        return spiderListeners;
    }

    public Spider setSpiderListeners(List<SpiderListener> spiderListeners) {
        this.spiderListeners = spiderListeners;
        return this;
    }

    public Date getStartTime() {
        return startTime;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    /**
     * Set wait time when no url is polled.<br><br>
     *
     * @param emptySleepTime In MILLISECONDS.
     * @return this
     */
    public Spider setEmptySleepTime(long emptySleepTime) {
        if(emptySleepTime<=0){
            throw new IllegalArgumentException("emptySleepTime should be more than zero!");
        }
        this.emptySleepTime = emptySleepTime;
        return this;
    }
}
