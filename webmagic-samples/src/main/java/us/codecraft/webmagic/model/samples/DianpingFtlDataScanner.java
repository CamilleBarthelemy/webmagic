package us.codecraft.webmagic.model.samples;

import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.model.AfterExtractor;
import us.codecraft.webmagic.model.OOSpider;
import us.codecraft.webmagic.model.annotation.ExtractBy;
import us.codecraft.webmagic.model.annotation.TargetUrl;

import java.util.List;

/**
 * @author yihua.huang@dianping.com <br>
 *         Date: 13-8-13 <br>
 *         Time: 上午10:13 <br>
 */
@TargetUrl("http://*.alpha.dp/*")
public class DianpingFtlDataScanner implements AfterExtractor {

	private static final int MIN_DATA_SIZE_TO_PRINT = 1;
	private static final int MIN_FIRST_DATA_LENGTH_TO_PRINT = 100;
	private static final int THREAD_COUNT = 5;

	@ExtractBy(value = "(DP\\.data\\(\\{.*\\}\\));", type = ExtractBy.Type.Regex, notNull = true, multi = true)
	private List<String> data;

	public static void main(String[] args) {
		OOSpider.create(Site.me().setSleepTime(0), DianpingFtlDataScanner.class)
				.thread(THREAD_COUNT).run();
	}

	@Override
	public void afterProcess(Page page) {
		if (data.size() > MIN_DATA_SIZE_TO_PRINT) {
			System.err.println(page.getUrl());
		}
		if (data.size() > 0 && data.get(0).length() > MIN_FIRST_DATA_LENGTH_TO_PRINT) {
			System.err.println(page.getUrl());
		}
	}
}
