//package com.scurab.android.zumpareader;
//
//
//import com.scurab.android.zumpareader.model.Survey;
//import com.scurab.android.zumpareader.model.ZumpaMainPageResult;
//import com.scurab.android.zumpareader.model.ZumpaThread;
//import com.scurab.android.zumpareader.model.ZumpaThreadItem;
//import com.scurab.android.zumpareader.model.ZumpaThreadResult;
//import com.scurab.android.zumpareader.retrofit.ZumpaConverterFactory;
//import com.scurab.android.zumpareader.util.ParseUtils;
//import com.scurab.android.zumpareader.reader.ZumpaSimpleParser;
//
//import org.junit.Test;
//import org.junit.runner.RunWith;
//
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.LinkedHashMap;
//
//import retrofit.Call;
//import retrofit.Response;
//import retrofit.Retrofit;
//
//import static org.junit.Assert.assertEquals;
//import static org.junit.Assert.assertNotNull;
//import static org.junit.Assert.assertTrue;
//
//
///**
// * To work on unit tests, switch the Test Artifact in the Build Variants view.
// */
//@RunWith(TestRunner.class)
//public class ZumpaSimpleParserTest {
//
//    @Test
//    public void testParseContent() throws Exception {
//        Retrofit retrofit = new Retrofit.Builder()
//                .baseUrl(ZR.Constants.ZUMPA_MAIN_URL)
//                .addConverterFactory(new ZumpaConverterFactory(new ZumpaSimpleParser()))
//                .build();
//        ZumpaAPI zumpaAPI = retrofit.create(ZumpaAPI.class);
//        Call<ZumpaMainPageResult> mainPage = zumpaAPI.getMainPage();
//        Response<ZumpaMainPageResult> execute = mainPage.execute();
//        assertNotNull(execute.body());
//    }
//
//    @Test
//    public void testParseContentPage2() throws Exception {
//        Retrofit retrofit = new Retrofit.Builder()
//                .baseUrl(ZR.Constants.ZUMPA_MAIN_URL)
//                .addConverterFactory(new ZumpaConverterFactory(new ZumpaSimpleParser()))
//                .build();
//        ZumpaAPI zumpaAPI = retrofit.create(ZumpaAPI.class);
//        Call<ZumpaMainPageResult> call = zumpaAPI.getMainPage();
//        Response<ZumpaMainPageResult> execute = call.execute();
//
//        LinkedHashMap<String, ZumpaThread> items = execute.body().getItems();
//
//        ArrayList<ZumpaThread> threads = new ArrayList<>(items.values());
//        ZumpaThread zumpaThread = threads.get(threads.size() - 1);
//        call = zumpaAPI.getMainPage(zumpaThread.getId());
//        ZumpaMainPageResult body = call.execute().body();
//        LinkedHashMap<String, ZumpaThread> items1 = body.getItems();
//
//        threads = new ArrayList<>(items1.values());
//        zumpaThread = threads.get(threads.size() - 1);
//        call = zumpaAPI.getMainPage(zumpaThread.getId());
//        body = call.execute().body();
//        LinkedHashMap<String, ZumpaThread> items2 = body.getItems();
//    }
//
//    @Test
//    public void testParseDetail() throws Exception {
//        Retrofit retrofit = new Retrofit.Builder()
//                .baseUrl(ZR.Constants.ZUMPA_MAIN_URL)
//                .addConverterFactory(new ZumpaConverterFactory(new ZumpaSimpleParser()))
//                .build();
//
//        ZumpaAPI zumpaAPI = retrofit.create(ZumpaAPI.class);
//        Call<ZumpaMainPageResult> mainPage = zumpaAPI.getMainPage();
//        Response<ZumpaMainPageResult> execute = mainPage.execute();
//
//        ZumpaMainPageResult body = execute.body();
//
//        ZumpaThread next = body.getItems().values().iterator().next();
//        Call<ZumpaThreadResult> threadPage = zumpaAPI.getThreadPage(next.getId(), next.getId());
//        Response<ZumpaThreadResult> execute1 = threadPage.execute();
//        assertNotNull(execute1.body());
//    }
//
//    @Test
//    public void testParseAuthor() throws Exception {
//        Retrofit retrofit = new Retrofit.Builder()
//                .baseUrl(ZR.Constants.ZUMPA_MAIN_URL)
//                .addConverterFactory(new ZumpaConverterFactory(new ZumpaSimpleParser()))
//                .build();
//
//        ZumpaAPI zumpaAPI = retrofit.create(ZumpaAPI.class);
//        Call<ZumpaMainPageResult> mainPage = zumpaAPI.getMainPage();
//        Response<ZumpaMainPageResult> execute = mainPage.execute();
//
//        ZumpaMainPageResult body = execute.body();
//
//        ZumpaThread next = body.getItems().values().iterator().next();
//        String x = "1693935";
//        Call<ZumpaThreadResult> threadPage = zumpaAPI.getThreadPage(x, x);
//        Response<ZumpaThreadResult> execute1 = threadPage.execute();
//        assertNotNull(execute1.body());
//    }
//
//    @Test
//    public void testSurvey() throws IOException {
//        String id = "1681570";
//
//        Retrofit retrofit = new Retrofit.Builder()
//                .baseUrl(ZR.Constants.ZUMPA_MAIN_URL)
//                .addConverterFactory(new ZumpaConverterFactory(new ZumpaSimpleParser()))
//                .build();
//
//        ZumpaAPI zumpaAPI = retrofit.create(ZumpaAPI.class);
//        Call<ZumpaThreadResult> threadPage = zumpaAPI.getThreadPage(id, id);
//        ZumpaThreadResult body = threadPage.execute().body();
//        ZumpaThreadItem first = body.getItems().iterator().next();
//
//        Survey survey = first.getSurvey();
//        assertNotNull(survey);
//
//        assertNotNull(survey.getItems());
//        assertTrue(survey.getItems().size() > 0);
//        assertNotNull(survey.getQuestion());
//        assertNotNull(survey.getId());
//        assertTrue(survey.getResponses() > 0);
//    }
//
//    @Test
//    public void testParseLink() {
//        String link = "http://www.qqq.cz";
//        String linkNoScheme = link.replace("http://", "");
//        String link1 = String.format("<a href=\"%1$s\">%1$s</a>", link);
//        String link2 = link;
//
//        assertEquals(link, ParseUtils.Companion.parseLink(link1));
//        assertEquals(link, ParseUtils.Companion.parseLink(link2));
//        assertEquals(linkNoScheme, ParseUtils.Companion.parseLink(linkNoScheme));
//    }
//}