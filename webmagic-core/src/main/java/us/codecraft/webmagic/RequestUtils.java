package us.codecraft.webmagic;
import us.codecraft.webmagic.scheduler.Scheduler;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;

public class RequestUtils {

    public static void addUrlsToRequests(List<String> urls, List<Request> requests) {
        for (String url : urls) {
            requests.add(new Request(url));
        }
    }


    public static void startWithUrls(List<String> urls, Spider spider, boolean spawnUrl, Scheduler scheduler, boolean startNow) {
        List<Request> requests = new ArrayList<>();
        addUrlsToRequests(urls, requests);
        spider = spider.startRequest(requests)
                .setSpawnUrl(spawnUrl)
                .setScheduler(scheduler);
        if (startNow) {
            spider.runAsync();
        }
    }


    public static void extractAndAddRequestsFromPage(Page page, Spider spider, boolean spawnUrl) {
        if (spawnUrl && CollectionUtils.isNotEmpty(page.getTargetRequests())) {
            for (Request request : page.getTargetRequests()) {
                spider.addRequest(request);
            }
        }
        spider.signalNewUrl();
    }

}

