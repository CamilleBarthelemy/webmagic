package us.codecraft.webmagic;

import java.util.List;

import org.apache.commons.collections4.CollectionUtils;

public class SuccessHandler {

    protected static void onSuccess(Request request, List<SpiderListener> spiderListeners) {
        if (CollectionUtils.isNotEmpty(spiderListeners)) {
            for (SpiderListener spiderListener : spiderListeners) {
                spiderListener.onSuccess(request);
            }
        }
    }
}
