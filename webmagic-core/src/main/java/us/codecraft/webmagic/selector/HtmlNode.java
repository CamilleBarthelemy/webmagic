package us.codecraft.webmagic.selector;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/**
 * @author code4crafer@gmail.com
 */
public class HtmlNode extends AbstractSelectable {

    private final List<Element> elements;

    public HtmlNode(List<Element> elements) {
        this.elements = elements;
    }

    public HtmlNode() {
        elements = null;
    }

    protected List<Element> getElements() {
        return elements;
    }

    @Override
    public Selectable smartContent() {
        SmartContentSelector smartContentSelector = Selectors.smartContent();
        return select(smartContentSelector, getSourceTexts());
    }

    @Override
    public Selectable links() {
        ElementUtil elementUtil = new ElementUtil();
        return elementUtil.selectElements(new LinksSelector());
    }

    @Override
    public Selectable xpath(String xpath) {
        ElementUtil elementUtil = new ElementUtil();
        XpathSelector xpathSelector = Selectors.xpath(xpath);
        return elementUtil.selectElements(xpathSelector);
    }

    @Override
    public Selectable selectList(Selector selector) {
        if (selector instanceof BaseElementSelector) {
            ElementUtil elementUtil = new ElementUtil();
            return elementUtil.selectElements((BaseElementSelector) selector);
        }
        return selectList(selector, getSourceTexts());
    }

    @Override
    public Selectable select(Selector selector) {
        return selectList(selector);
    }

    @Override
    public Selectable $(String selector) {
        ElementUtil elementUtil = new ElementUtil();
        CssSelector cssSelector = Selectors.$(selector);
        return elementUtil.selectElements(cssSelector);
    }

    @Override
    public Selectable $(String selector, String attrName) {
        ElementUtil elementUtil = new ElementUtil();
        CssSelector cssSelector = Selectors.$(selector, attrName);
        return elementUtil.selectElements(cssSelector);
    }

    @Override
    public List<Selectable> nodes() {
        List<Selectable> selectables = new ArrayList<Selectable>();
        for (Element element : getElements()) {
            List<Element> childElements = new ArrayList<Element>(1);
            childElements.add(element);
            selectables.add(new HtmlNode(childElements));
        }
        return selectables;
    }

    @Override
    protected List<String> getSourceTexts() {
        List<String> sourceTexts = new ArrayList<String>(getElements().size());
        for (Element element : getElements()) {
            sourceTexts.add(element.toString());
        }
        return sourceTexts;
    }
}
