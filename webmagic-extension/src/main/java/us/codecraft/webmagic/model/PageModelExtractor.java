package us.codecraft.webmagic.model;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.model.annotation.*;
import us.codecraft.webmagic.model.formatter.ObjectFormatter;
import us.codecraft.webmagic.model.formatter.ObjectFormatterBuilder;
import us.codecraft.webmagic.selector.*;
import us.codecraft.webmagic.utils.ClassUtils;
import us.codecraft.webmagic.utils.ExtractorUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static us.codecraft.webmagic.model.annotation.ExtractBy.Source.RawText;

/**
 * The main internal logic of page model extractor.
 *
 * @author code4crafter@gmail.com <br>
 * @since 0.2.0
 */
class PageModelExtractor {

    private List<Pattern> targetUrlPatterns = new ArrayList<>();

    private Selector targetUrlRegionSelector;

    private List<Pattern> helpUrlPatterns = new ArrayList<>();

    private Selector helpUrlRegionSelector;

    private Class clazz;

    private List<FieldExtractor> fieldExtractors;

    private Extractor objectExtractor;

    private Logger logger = LoggerFactory.getLogger(getClass());

    private String EXTRACT_FAIL = "extract fail";

    Class getClazz() { return clazz;
    }

    List<Pattern> getTargetUrlPatterns() {
        return targetUrlPatterns;
    }

    List<Pattern> getHelpUrlPatterns() {
        return helpUrlPatterns;
    }

    Selector getTargetUrlRegionSelector() {
        return targetUrlRegionSelector;
    }

    Selector getHelpUrlRegionSelector() {
        return helpUrlRegionSelector;
    }

    public static PageModelExtractor create(Class clazz) {
        PageModelExtractor pageModelExtractor = new PageModelExtractor();
        pageModelExtractor.init(clazz);
        return pageModelExtractor;
    }

    private void init(Class clazz) {
        this.clazz = clazz;
        initClassExtractors();
        fieldExtractors = new ArrayList<>();
        for (Field field : ClassUtils.getFieldsIncludeSuperClass(clazz)) {
            field.setAccessible(true);
            FieldExtractor fieldExtractor = getAnnotationExtractBy(clazz, field);
            FieldExtractor fieldExtractorTmp = getAnnotationExtractCombo(clazz, field);
            if (fieldExtractor != null && fieldExtractorTmp != null) {
                throw new IllegalStateException("Only one of 'ExtractBy ComboExtract ExtractByUrl' can be added to a field!");
            } else if (fieldExtractor == null && fieldExtractorTmp != null) {
                fieldExtractor = fieldExtractorTmp;
            }
            fieldExtractorTmp = getAnnotationExtractByUrl(clazz, field);
            if (fieldExtractor != null && fieldExtractorTmp != null) {
                throw new IllegalStateException("Only one of 'ExtractBy ComboExtract ExtractByUrl' can be added to a field!");
            } else if (fieldExtractor == null && fieldExtractorTmp != null) {
                fieldExtractor = fieldExtractorTmp;
            }
            if (fieldExtractor != null) {
                fieldExtractor.setObjectFormatter(new ObjectFormatterBuilder().setField(field).build());
                fieldExtractors.add(fieldExtractor);
            }
        }
    }

    private FieldExtractor getAnnotationExtractByUrl(Class clazz, Field field) {
        FieldExtractor fieldExtractor = null;
        ExtractByUrl extractByUrl = field.getAnnotation(ExtractByUrl.class);
        if (extractByUrl != null) {
            String regexPattern = extractByUrl.value();
            if (regexPattern.trim().equals("")) {
                regexPattern = ".*";
            }
            fieldExtractor = new FieldExtractor(field,
                    new RegexSelector(regexPattern), FieldExtractor.Source.Url, extractByUrl.notNull(),
                    extractByUrl.multi() || List.class.isAssignableFrom(field.getType()));
            Method setterMethod = getSetterMethod(clazz, field);
            if (setterMethod != null) {
                fieldExtractor.setSetterMethod(setterMethod);
            }
        }
        return fieldExtractor;
    }

    private FieldExtractor getAnnotationExtractCombo(Class clazz, Field field) {
        FieldExtractor fieldExtractor = null;
        ComboExtract comboExtract = field.getAnnotation(ComboExtract.class);
        if (comboExtract != null) {
            ExtractBy[] extractBies = comboExtract.value();
            Selector selector;
            switch (comboExtract.op()) {
                case And:
                    selector = new AndSelector(ExtractorUtils.getSelectors(extractBies));
                    break;
                case Or:
                    selector = new OrSelector(ExtractorUtils.getSelectors(extractBies));
                    break;
                default:
                    selector = new AndSelector(ExtractorUtils.getSelectors(extractBies));
            }
            fieldExtractor = new FieldExtractor(field, selector, comboExtract.source() == ComboExtract.Source.RawHtml ? FieldExtractor.Source.RawHtml : FieldExtractor.Source.Html,
                    comboExtract.notNull(), comboExtract.multi() || List.class.isAssignableFrom(field.getType()));
            Method setterMethod = getSetterMethod(clazz, field);
            if (setterMethod != null) {
                fieldExtractor.setSetterMethod(setterMethod);
            }
        }
        return fieldExtractor;
    }

    private FieldExtractor getAnnotationExtractBy(Class clazz, Field field) {
        FieldExtractor fieldExtractor = null;
        ExtractBy extractBy = field.getAnnotation(ExtractBy.class);
        if (extractBy != null) {
            Selector selector = ExtractorUtils.getSelector(extractBy);
            ExtractBy.Source source0 = extractBy.source();
            if (extractBy.type()== ExtractBy.Type.JsonPath){
                source0 = RawText;
            }
            FieldExtractor.Source source = null;
            switch (source0){
                case RawText:
                    source = FieldExtractor.Source.RawText;
                    break;
                case RawHtml:
                    source = FieldExtractor.Source.RawHtml;
                    break;
                case SelectedHtml:
                    source =FieldExtractor.Source.Html;
                    break;
                default:
                    source =FieldExtractor.Source.Html;

            }

            fieldExtractor = new FieldExtractor(field, selector, source,
                    extractBy.notNull(), List.class.isAssignableFrom(field.getType()));
            fieldExtractor.setSetterMethod(getSetterMethod(clazz, field));
        }
        return fieldExtractor;
    }

    public static Method getSetterMethod(Class clazz, Field field) {
        String name = "set" + StringUtils.capitalize(field.getName());
        try {
            Method declaredMethod = clazz.getDeclaredMethod(name, field.getType());
            declaredMethod.setAccessible(true);
            return declaredMethod;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private void initClassExtractors() {
        Annotation annotation = clazz.getAnnotation(TargetUrl.class);
        if (annotation == null) {
            targetUrlPatterns.add(Pattern.compile(".*"));
        } else {
            TargetUrl targetUrl = (TargetUrl) annotation;
            String[] value = targetUrl.value();
            for (String s : value) {
                targetUrlPatterns.add(Pattern.compile(s.replace(".", "\\.").replace("*", "[^\"'#]*")));
            }
            if (!targetUrl.sourceRegion().equals("")) {
                targetUrlRegionSelector = new XpathSelector(targetUrl.sourceRegion());
            }
        }
        annotation = clazz.getAnnotation(HelpUrl.class);
        if (annotation != null) {
            HelpUrl helpUrl = (HelpUrl) annotation;
            String[] value = helpUrl.value();
            for (String s : value) {
                helpUrlPatterns.add(Pattern.compile(s.replace(".", "\\.").replace("*", "[^\"'#]*")));
            }
            if (!helpUrl.sourceRegion().equals("")) {
                helpUrlRegionSelector = new XpathSelector(helpUrl.sourceRegion());
            }
        }
        annotation = clazz.getAnnotation(ExtractBy.class);
        if (annotation != null) {
            ExtractBy extractBy = (ExtractBy) annotation;
            objectExtractor = new Extractor(new XpathSelector(extractBy.value()), Extractor.Source.Html, extractBy.notNull(), extractBy.multi());
        }
    }

    public Object process(Page page) {
        boolean matched = false;
        for (Pattern targetPattern : targetUrlPatterns) {
            if (targetPattern.matcher(page.getUrl().toString()).matches()) {
                matched = true;
            }
        }
        if (!matched) {
            return null;
        }
        if (objectExtractor == null) {
            return processSingle(page, null, true);
        } else {
            if (objectExtractor.multi) {
                List<Object> os = new ArrayList<>();
                List<String> list = objectExtractor.getSelector().selectList(page.getRawText());
                for (String s : list) {
                    Object o = processSingle(page, s, false);
                    if (o != null) {
                        os.add(o);
                    }
                }
                return os;
            } else {
                String select = objectExtractor.getSelector().select(page.getRawText());
                Object o = processSingle(page, select, false);
                return o;
            }
        }
    }

    private Object processSingle(Page page, String html, boolean isRaw) {
        Object o = null;
        try {
            o = clazz.newInstance();
            for (FieldExtractor fieldExtractor : fieldExtractors) {
                if (fieldExtractor.isMulti()) {
                    List<Object> convertedValues = extractAndConvertMultiValues(page, html, isRaw, fieldExtractor);
                    if (convertedValues == null && fieldExtractor.isNotNull()) {
                        return null;
                    }
                    setField(o, fieldExtractor, convertedValues);
                } else {
                    Object convertedValue = extractAndConvertSingleValue(page, html, isRaw, fieldExtractor);
                    if (convertedValue == null && fieldExtractor.isNotNull()) {
                        return null;
                    }
                    setField(o, fieldExtractor, convertedValue);
                }
            }
            executeAfterProcess(o, page);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("An error occurred during object processing", e);
        }
        return o;
    }


    private List<Object> extractAndConvertMultiValues(Page page, String html, boolean isRaw, FieldExtractor fieldExtractor) {
        List<String> values = extractValues(page, html, isRaw, fieldExtractor);
        if (values == null || values.isEmpty()) {
            return null;
        }
        if (fieldExtractor.getObjectFormatter() != null) {
            return convert(values, fieldExtractor.getObjectFormatter());
        } else {
            return new ArrayList<>(values);
        }
    }


    private Object extractAndConvertSingleValue(Page page, String html, boolean isRaw, FieldExtractor fieldExtractor) {
        String value = extractSingleValue(page, html, isRaw, fieldExtractor);
        if (value == null) {
            return null;
        }
        if (fieldExtractor.getObjectFormatter() != null) {
            return convert(value, fieldExtractor.getObjectFormatter());
        } else {
            return value;
        }
    }


    private String extractSingleValue(Page page, String html, boolean isRaw, FieldExtractor fieldExtractor) {
        switch (fieldExtractor.getSource()) {
            case RawHtml:
                return isRaw ? page.getHtml().selectDocument(fieldExtractor.getSelector()) : null;
            case Html:
                return isRaw ? page.getHtml().selectDocument(fieldExtractor.getSelector()) : fieldExtractor.getSelector().select(html);
            case Url:
                return fieldExtractor.getSelector().select(page.getUrl().toString());
            case RawText:
                return fieldExtractor.getSelector().select(page.getRawText());
            default:
                return fieldExtractor.getSelector().select(html);
        }
    }

    private List<String> extractValues(Page page, String html, boolean isRaw, FieldExtractor fieldExtractor) {
        switch (fieldExtractor.getSource()) {
            case RawHtml:
                return isRaw ? page.getHtml().selectDocumentForList(fieldExtractor.getSelector()) : null;
            case Html:
                return isRaw ? page.getHtml().selectDocumentForList(fieldExtractor.getSelector()) : fieldExtractor.getSelector().selectList(html);
            case Url:
                return fieldExtractor.getSelector().selectList(page.getUrl().toString());
            case RawText:
                return fieldExtractor.getSelector().selectList(page.getRawText());
            default:
                return fieldExtractor.getSelector().selectList(html);
        }
    }


    private void executeAfterProcess(Object o, Page page) {
        if (o instanceof AfterExtractor) {
            ((AfterExtractor) o).afterProcess(page);
        }
    }


    private Object convert(String value, ObjectFormatter objectFormatter) {
        try {
            Object format = objectFormatter.format(value);
            logger.debug("String {} is converted to {}", value, format);
            return format;
        } catch (Exception e) {
            logger.error("convert " + value + " to " + objectFormatter.clazz() + " error!", e);
        }
        return null;
    }

    private List<Object> convert(List<String> values, ObjectFormatter objectFormatter) {
        List<Object> objects = new ArrayList<>();
        for (String value : values) {
            Object converted = convert(value, objectFormatter);
            if (converted != null) {
                objects.add(converted);
            }
        }
        return objects;
    }

    private void setField(Object o, FieldExtractor fieldExtractor, Object value) throws IllegalAccessException, InvocationTargetException {
        if (value == null) {
            return;
        }
        if (fieldExtractor.getSetterMethod() != null) {
            fieldExtractor.getSetterMethod().invoke(o, value);
        }
        fieldExtractor.getField().set(o, value);
    }
}