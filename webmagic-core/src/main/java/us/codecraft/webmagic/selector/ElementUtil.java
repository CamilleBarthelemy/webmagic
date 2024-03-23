/**
 * Utility class for working with HTML elements and selectors.
 */
package us.codecraft.webmagic.selector;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public class ElementUtil {

    /**
     * An instance of HtmlNode for HTML node operations.
     */
    public HtmlNode htmlNode = new HtmlNode();

    /**
     * Select elements based on the given element selector.
     * @param elementSelector the element selector to use for selecting elements
     * @return a Selectable object containing the selected elements or plaintext, depending on the element selector's attributes
     */
    public Selectable selectElements(BaseElementSelector elementSelector) {
        ListIterator<Element> elementIterator = htmlNode.getElements().listIterator();
        if (!elementSelector.hasAttribute()) {
            List<Element> resultElements = new ArrayList<>();
            while (elementIterator.hasNext()) {
                Element element = checkElementAndConvert(elementIterator);
                List<Element> selectElements = elementSelector.selectElements(element);
                resultElements.addAll(selectElements);
            }
            return new HtmlNode(resultElements);
        } else {
            // has attribute, consider as plaintext
            List<String> resultStrings = new ArrayList<String>();
            while (elementIterator.hasNext()) {
                Element element = checkElementAndConvert(elementIterator);
                List<String> selectList = elementSelector.selectList(element);
                resultStrings.addAll(selectList);
            }
            return new PlainText(resultStrings);

        }
    }

    /**
     * Checks if the element is an instance of Document, and converts it if necessary.
     * @param elementIterator the element iterator containing the element to check and possibly convert
     * @return the checked and possibly converted element
     */
    public Element checkElementAndConvert(ListIterator<Element> elementIterator) {
        Element element = elementIterator.next();
        if (!(element instanceof Document)) {
            Document root = new Document(element.ownerDocument().baseUri());
            Element clone = element.clone();
            root.appendChild(clone);
            elementIterator.set(root);
            return root;
        }
        return element;
    }
}
