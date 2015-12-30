package com.scurab.android.zumpareader.reader;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.Pair;
import android.text.Html;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.TypefaceSpan;

import com.scurab.android.zumpareader.R;
import com.scurab.android.zumpareader.model.Survey;
import com.scurab.android.zumpareader.model.SurveyItem;
import com.scurab.android.zumpareader.model.ZumpaMainPageResult;
import com.scurab.android.zumpareader.model.ZumpaThread;
import com.scurab.android.zumpareader.model.ZumpaThreadItem;
import com.scurab.android.zumpareader.model.ZumpaThreadResult;
import com.scurab.android.zumpareader.util.ParseUtils;
import com.scurab.android.zumpareader.ZR;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by JBruchanov on 24/11/2015.
 */
@SuppressLint("SimpleDateFormat")
public class ZumpaSimpleParser {

    private boolean mShowLastUser;
    private static final SimpleDateFormat FULL_DATE_FORMAT = new SimpleDateFormat("dd. MM. yyyy HH:mm:ss");
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm");
    private static final Pattern URL_PATTERN = Pattern.compile("(http[s]?://[^\\s]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_PATTERN = Pattern.compile("Datum:&nbsp;([^<]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern AUTHOR_PATTERN = Pattern.compile("Autor:&nbsp;([^<]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern AUTHOR_PATTERN1 = Pattern.compile("Autor:&nbsp;<a[^>]*>([^<]+)</a>", Pattern.CASE_INSENSITIVE);
    private static final Pattern AUTHOR_PATTERN2 = Pattern.compile("Autor:&nbsp;(.+)<br>Datum:", Pattern.CASE_INSENSITIVE);
    private static Pattern SURVEY_RESPONSE_PATTERN = Pattern.compile("\\((\\d*) odp.\\)", Pattern.CASE_INSENSITIVE);
    private static Pattern ZUMPA_LINK = Pattern.compile("portal2.dkm.cz/phorum/read.php.*t=(\\d+)", Pattern.CASE_INSENSITIVE);
    private static Pattern IMG_OBJECT = Pattern.compile("<img.*src=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final String TAG_NBSP = "&nbsp;";
    private String mUserName;

    public ZumpaMainPageResult parseMainPage(@NonNull String html) {
        return parseMainPage(Jsoup.parse(html));
    }

    public ZumpaMainPageResult parseMainPage(@NonNull InputStream stream) throws IOException {
        return parseMainPage(Jsoup.parse(stream, ZR.Constants.ENCODING, ""));
    }

    public ZumpaMainPageResult parseMainPage(Document doc) {
        dispatchParsingStarting();

        Elements elems = doc.getElementsByTag(HTMLTags.TAG_TABLE);
        ListIterator<Element> li = elems.listIterator();
        Element topTable = li.next();
        Element mainTable = li.next();
        Element bottomTable = li.next();

        Pair<String, String> links = handleParsingTopTable(topTable);
        LinkedHashMap<String, ZumpaThread> items = parseContent(mainTable);

        dispatchParsingFinished();

        return new ZumpaMainPageResult(links.first, links.second, items);
    }

    public boolean isShowLastUser() {
        return mShowLastUser;
    }

    public void setShowLastUser(boolean showLastUser) {
        mShowLastUser = showLastUser;
    }

    //region MainPage
    private Pair<String, String> handleParsingTopTable(@NonNull Element elem) {
        Elements cols = elem.getElementsByTag(HTMLTags.TAG_TABLE_COLUMS);
        Elements els = cols.get(cols.size() - 1).getElementsByTag(HTMLTags.TAG_HREF);
        int size = els.size();

        String next = null, prev = null;
        if (size == 1) {// no prev page
            next = els.get(0).attr(HTMLTags.ATTR_HREF);
        } else if (size == 2) {
            prev = els.get(0).attr(HTMLTags.ATTR_HREF);
            next = els.get(1).attr(HTMLTags.ATTR_HREF);
        }
        return new Pair<>(prev, next);
    }

    private LinkedHashMap<String, ZumpaThread> parseContent(Element elem) {
        LinkedHashMap<String, ZumpaThread> result = new LinkedHashMap<>();

        Elements elems = elem.getElementsByTag(HTMLTags.TAG_TABLE_ROW);
        ListIterator<Element> li = elems.listIterator();
        Element el = null;
        li.next();// first line is header
        while (li.hasNext()) {
            el = li.next();
            Elements columns = el.getElementsByTag(HTMLTags.TAG_TABLE_COLUMS);
            ZumpaThread zumpaItem = parseThread(columns);
            if (zumpaItem != null) {
                dispatchParsedItem(zumpaItem);
                result.put(zumpaItem.getId(), zumpaItem);
            }
        }
        return result;
    }

    private ZumpaThread parseThread(Elements columns) {
        ListIterator<Element> li = columns.listIterator();
        Element first = li.next();

        Element subElem = first.getElementsByTag(HTMLTags.TAG_HREF).first();
        String url = subElem.attr(HTMLTags.ATTR_HREF);
        String id = subElem.attr(HTMLTags.ATTR_REL);
        String subject = subElem.text();

        Element second = li.next();
        String authorHtml = second.html().replace(HTMLTags.NBSP, "").trim();

        Element third = li.next();
        String answers = third.text();

        Element fourth = li.next(); // read
        Element fifth = li.next(); // complete

        Element sixth = li.next(); // complete
        String time = null;
        String lastAnswerAuthor = null;

        if (mShowLastUser) {
            try {
                String sub = sixth.text();
                String[] vals = sub.split(String.valueOf(HTMLTags.NBSP_CHAR));
                time = vals[0];
                lastAnswerAuthor = vals[vals.length - 1];// can be date between
            } catch (Throwable t) {
                time = sixth.text();
            }
        } else {
            time = sixth.text();
        }
        time = time.replace(HTMLTags.NBSP_CHAR, ' ');


        boolean isFavourite = getIsFavourite(first);
        boolean hasResponseForYou = getHasResponseForYou(first);
        int responses = safeInt(answers, 0);

        String author = Html.fromHtml(authorHtml).toString();
        String contentUrl = ZR.Constants.ZUMPA_MAIN_URL + url;
        long timeValue = safeParse(mShowLastUser ? TIME_FORMAT : FULL_DATE_FORMAT, time);

        ZumpaThread zumpaThread = new ZumpaThread(id, subject);
        zumpaThread.setAuthor(author);
        zumpaThread.setContentUrl(contentUrl);
        zumpaThread.setTime(timeValue);
        zumpaThread.setItems(responses, mUserName);
        zumpaThread.setFavorite(isFavourite);
        return zumpaThread;
    }

    private boolean getIsFavourite(Element first) {
        boolean result = false;
        if (first.hasAttr(HTMLTags.CLASS)) {
            String cls = first.attr(HTMLTags.CLASS);
            result = cls.equals(HTMLTags.IS_FAVOURITE_CLASS);
        }
        return result;
    }

    private boolean getHasChange(Element elem) {
        boolean result = false;
        Elements subImgs = elem.getElementsByTag(HTMLTags.TAG_IMG);
        if (subImgs.size() > 0) {
            Element e = elem.getElementsByTag(HTMLTags.TAG_IMG).first();
            if (e.hasAttr(HTMLTags.ATTR_TITLE)) {
                result = e.attr(HTMLTags.ATTR_TITLE).contains("!");
            }
        }

        return result;
    }

    private boolean getHasResponseForYou(Element elem) {
        boolean result = false;
        Elements subImgs = elem.getElementsByTag(HTMLTags.TAG_IMG);
        if (subImgs.size() > 0) {
            Element e = elem.getElementsByTag(HTMLTags.TAG_IMG).first();
            if (e.hasAttr(HTMLTags.ATTR_TITLE)) {
                result = e.attr(HTMLTags.ATTR_TITLE).contains("*");
            }
        }
        return result;
    }

    private static int safeInt(String text, int defValue) {
        try {
            text = text.replaceAll(HTMLTags.NBSP_CHAR_STR, "").trim();
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
        return 0;
    }

    private static long safeParse(SimpleDateFormat parser, String value) {
        try {
            return parser.parse(value).getTime();
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return 0;
    }

    private void dispatchParsingStarting() {

    }

    private void dispatchParsingFinished() {

    }

    private void dispatchParsedItem(ZumpaThread item) {

    }
    //endregion MainPage


    //region thread
    public ZumpaThreadResult parseThread(@NonNull InputStream stream, @Nullable String userName) throws IOException {
        return parseThread(Jsoup.parse(stream, ZR.Constants.ENCODING, ""), userName);
    }

    public ZumpaThreadResult parseThread(@NonNull Document doc, @Nullable String userName) {
        List<ZumpaThreadItem> result = new ArrayList<>();

        Elements elems = doc.getElementsByTag(HTMLTags.TAG_TABLE);
        int size = elems.size();
        if (size > 0) // should be always at min 1
        {
            ListIterator<Element> li = elems.listIterator();
            li.next();// header

            int limit = size - 5;
            for (int i = 0; i < limit; i++) {// 4 je footer
                // Element header = li.next();//header
                i++;

                Element parent = li.next();
                Element elem = parent.getElementsByTag(HTMLTags.TAG_TABLE_ROW).get(1); // to get inside row where is final table
                elem = elem.getElementsByTag(HTMLTags.TAG_TABLE).first(); //get final table
                ZumpaThreadItem zsi = parseThreadItem(elem, userName);
                result.add(zsi);

                li.next();// footer
                Element footer = li.next();// footer

                zsi.setAuthorReal(getAuthorNameFromResponse(footer));
                zsi.setOwnThread(userName != null && userName.equals(zsi.getAuthorReal()));

                if (i == 1) { // only first element can contains survey
                    zsi.setSurvey(parseSurvey(elem));
                }
                i++;
            }
        }
        return new ZumpaThreadResult(result);
    }

    private ZumpaThreadItem parseThreadItem(Element elem, String userName) {

        Elements elems = elem.getElementsByTag(HTMLTags.TAG_TABLE_COLUMS);
        int size = elems.size();
        ZumpaThreadItem zti = null;
        if (size == 1) // should be always 1
        {
            elem = elems.first();
            String content = elem.html();
            CharSequence author = getAuthorName(content);
            long date = getTime(content);

            StringBuilder sb = new StringBuilder();
            List<String> urls = null;
            boolean containsBold = false;

            String[] lines = content.split("<br>");
            for (int i = 3; i < lines.length; i++) {
                String line = lines[i];
                if (line.contains(HTMLTags.TAG_BOLD_START) && line.contains(HTMLTags.TAG_BOLD_END)) {
                    containsBold = true;
                }
                if (line.matches(".*http[s]?.*")) {
                    String link = ParseUtils.Companion.parseLink(line);
                    if (link != null) {
                        if (urls == null) {
                            urls = new ArrayList<>();
                        }
                        urls.add(link);
                    }
                }
                line = Html.fromHtml(line).toString();
                sb.append(line).append("\n");
            }

            String body = null;
            if (sb.length() > 0) {
                body = sb.substring(0, sb.length() - 1);
            } else {
                body = "";
            }

            zti = new ZumpaThreadItem(author, body, date);
            zti.setUrls(urls);
            if (!TextUtils.isEmpty(userName)) {
                zti.setHasResponseForYou(body.contains(createToMeTemplate(userName)));
            }
        }
        return zti;
    }

    private String createToMeTemplate(String userName) {
        return userName.length() > 0
                ? String.format("%s%s»", userName, HTMLTags.NBSP_CHAR)
                : String.format("%s%s»", System.currentTimeMillis(), HTMLTags.NBSP_CHAR);
    }

    private String getAuthorNameFromResponse(Element footer) {
        String data = footer.html();
        Pattern p = Pattern.compile("reply2\\(\\'@(.*):", Pattern.MULTILINE);
        Matcher pm = p.matcher(data);
        String name = "";
        if (pm.find()) {
            name = Html.fromHtml(pm.group(1)).toString();
        }
        return name;
    }

    private CharSequence getAuthorName(String html) {
        String name = getGroup(AUTHOR_PATTERN, html, 1, null);
        if (name == null) {
            name = getGroup(AUTHOR_PATTERN1, html, 1, null);
        }
        CharSequence result = name;
        if (name != null && name.contains("(")) {
            String htmlName = getGroup(AUTHOR_PATTERN2, html, 1, "");
            result = Html.fromHtml(htmlName);
        }
        return result;
    }

    private long getTime(String data) {
        long date = 0;
        try {
            String dateString = getGroup(DATE_PATTERN, data, 1, "");
            date = FULL_DATE_FORMAT.parse(dateString).getTime();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return date;
    }

    private static String getGroup(Pattern pattern, String value, int group, String defValue) {
        Matcher matcher = pattern.matcher(value);
        if (matcher.find()) {
            return matcher.group(group);
        }
        return defValue;
    }
    //endregion thread


    //region survey
    private Survey parseSurvey(Element subDoc) {
        Elements divs = subDoc.getElementsByTag(HTMLTags.TAG_DIV);
        Iterator<Element> iter = divs.iterator();
        while (iter.hasNext()) {
            Element e = iter.next();
            String id = e.attr(HTMLTags.ATTR_ID);
            if (id != null && id.matches("ank\\d*")) {
                return parseSurveyImpl(e, id);
            }
        }
        return null;
    }

    private Survey parseSurveyImpl(Element e, String id) {
        String question = parseQuestion(e);
        int responses = parseResponses(e);
        List<SurveyItem> surveyItems = parseSurveyItems(e);
        return new Survey(id, question, responses, surveyItems);
    }

    private String parseQuestion(Element e) {
        Elements spans = e.getElementsByTag(HTMLTags.TAG_SPAN);
        Element span = spans.first();
        return span.text();
    }

    private int parseResponses(Element e) {
        String txt = e.text().replace(HTMLTags.NBSP_CHAR, ' ');
        return getSurveyResponsesFromText(txt);
    }

    private List<SurveyItem> parseSurveyItems(Element e) {
        Elements lis = e.getElementsByTag(HTMLTags.TAG_LI);
        ListIterator<Element> iter = lis.listIterator();
        List<SurveyItem> result = new ArrayList<>();
        while (iter.hasNext()) {
            Element li = iter.next();
            SurveyItem surveyItem = parseSurveyRow(li);
            if (surveyItem != null) {
                result.add(surveyItem);
            }
        }
        return result;
    }

    private SurveyItem parseSurveyRow(Element li) {
        Element a = li.getElementsByTag(HTMLTags.TAG_HREF).first();
        if (a == null) {
            a = li.getElementsByTag(HTMLTags.TAG_BOLD).first();
        }
        Element innerDiv = li.getElementsByTag(HTMLTags.TAG_DIV).first();
        String percts = innerDiv.html().replace(HTMLTags.NBSP, "").replace("%", "").trim();
        String text = a.text();
        int percents = safeInt(percts, 0);
        return new SurveyItem(text, percents, false);
    }

    public static int getSurveyResponsesFromText(String text) {
        int result = -1;
        Matcher m = SURVEY_RESPONSE_PATTERN.matcher(text);
        if (m.find()) {
            try {
                result = Integer.parseInt(m.group(1));
            } catch (Exception e) {
                // dont need exception
            }
        }
        return result;
    }
    //endregion survey

    public static int getZumpaThreadId(String link) {
        if (link != null) {
            try {
                String value = getGroup(ZUMPA_LINK, link, 1, null);
                return value != null ? Integer.parseInt(value) : 0;
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
        return 0;
    }

    public static CharSequence parseBody(String body, Context context) {
        SpannableString ssb = new SpannableString(body);
        Matcher matcher = URL_PATTERN.matcher(body);
        List<Pair<Integer, Integer>> links = new ArrayList<>();
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            links.add(new Pair<>(start, end));
            setSpans(ssb, start, end,
                    new RelativeSizeSpan(0.5f),
                    new TypefaceSpan("monospace"));
        }
        //smileys
        for (Integer drawable : SmileRes.DATA.keySet()) {
            Pattern pattern = SmileRes.DATA.get(drawable);
            matcher = pattern.matcher(body);
            while (matcher.find()) {
                int start = matcher.start();
                int end = matcher.end();
                if (!ignore(links, start)) {
                    Drawable draw = context.getResources().getDrawable(drawable);
                    draw.setBounds(0, 0, (int) (draw.getIntrinsicWidth() / 1.5f), (int) (draw.getIntrinsicHeight() / 1.5f));
                    setSpans(ssb, start, end,
                            new ImageSpan(draw));
                }
            }
        }
        return ssb;
    }

    private static  boolean ignore(List<Pair<Integer, Integer>> pairs, int value) {
        for (Pair<Integer, Integer> p : pairs) {
            if (p.first <= value && value <= p.second) {
                return true;
            } else if (p.first > value) {
                break;
            }
        }
        return false;
    }

    private static void setSpans(SpannableString ssb, int start, int end, Object... spannables) {
        for (Object spannable : spannables) {
            ssb.setSpan(spannable, start, end, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        }
    }

    @Nullable
    public static String tryParseImage(String content) {
        return (content != null) ? getGroup(IMG_OBJECT, content, 1, null) : null;
    }

    public String getUserName() {
        return mUserName;
    }

    public void setUserName(String userName) {
        mUserName = userName;
    }

    public static class SmileRes {
        static HashMap<Integer, Pattern> DATA = new HashMap<>();

        static {
            DATA.put(R.drawable.emoji_hm, Pattern.compile(":[-o]?[/\\\\]"));
            DATA.put(R.drawable.emoji_lol, Pattern.compile(":[-o]?D+"));
            DATA.put(R.drawable.emoji_o_o, Pattern.compile("[oO]_[oO]"));
            DATA.put(R.drawable.emoji_p, Pattern.compile(":[-o]?[pP]"));
            DATA.put(R.drawable.emoji_sad, Pattern.compile(":[-o]?\\(+"));
            DATA.put(R.drawable.emoji_smiley, Pattern.compile(":[-o]?\\)+"));
            DATA.put(R.drawable.emoji_speechless, Pattern.compile(":[-o]?\\|"));
            DATA.put(R.drawable.emoji_wink, Pattern.compile(";[-o]?\\)+"));
        }
    }
}
