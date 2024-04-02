package us.codecraft.webmagic;

import java.util.List;

import org.apache.commons.collections4.CollectionUtils;

public class ErrorHandler {

    public static void onError(Request request, Exception e, List<SpiderListener> spiderListeners) {
        if (CollectionUtils.isNotEmpty(spiderListeners)) {
            for (SpiderListener spiderListener : spiderListeners) {
                spiderListener.onError(request, e);
            }
        }
    }
}

