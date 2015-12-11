package com.scurab.android.zumpareader;


import com.scurab.android.zumpareader.model.ZumpaLoginBody;
import com.scurab.android.zumpareader.model.ZumpaMainPageResult;
import com.scurab.android.zumpareader.model.ZumpaResponse;
import com.scurab.android.zumpareader.model.ZumpaThreadBody;
import com.scurab.android.zumpareader.model.ZumpaThreadResult;
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser;
import com.scurab.android.zumpareader.retrofit.ZumpaConverterFactory;
import com.scurab.android.zumpareader.util.ParseUtils;
import com.squareup.okhttp.Headers;
import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.logging.HttpLoggingInterceptor;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.HashSet;

import retrofit.Call;
import retrofit.Response;
import retrofit.Retrofit;
import retrofit.RxJavaCallAdapterFactory;
import rx.Observer;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;


/**
 * To work on unit tests, switch the Test Artifact in the Build Variants view.
 */
@RunWith(TestRunner.class)
public class ZumpaSimpleParserTest {

    @Test
    public void testParseContent() throws Exception {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(ZR.Constants.ZUMPA_MAIN_URL)
                .addConverterFactory(new ZumpaConverterFactory(new ZumpaSimpleParser()))
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .build();
        ZumpaAPI zumpaAPI = retrofit.create(ZumpaAPI.class);
        final boolean[] called = {false};
        zumpaAPI.getMainPage()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(new Observer<ZumpaMainPageResult>() {
                    @Override
                    public void onCompleted() {
                    }

                    @Override
                    public void onError(Throwable e) {
                        fail(e.getMessage());
                    }

                    @Override
                    public void onNext(ZumpaMainPageResult zumpaMainPageResult) {
                        assertNotNull(zumpaMainPageResult);
                        called[0] = true;
                        synchronized (called) {
                            called.notifyAll();
                        }
                    }
                });

        synchronized (called) {
            called.wait(5000);
            assertTrue(called[0]);
        }
    }
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


    @Test
    public void testLogin() throws IOException {
        OkHttpClient client = new OkHttpClient();
        HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor(new HttpLoggingInterceptor.Logger() {
            @Override
            public void log(String message) {
                System.out.println(message);
            }
        });
        client.setFollowRedirects(false);
        interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
        client.interceptors().add(interceptor);
        client.interceptors().add(new Interceptor() {
            @Override
            public com.squareup.okhttp.Response intercept(Chain chain) throws IOException {
                Request request = chain.request();
                com.squareup.okhttp.Response response = chain.proceed(request);
                Headers headers = response.headers();
                String sessionId = ParseUtils.Companion.extractPHPSessionId(headers);
                System.err.println("SessionId:" + sessionId);
                return response;
            }
        });

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(ZR.Constants.ZUMPA_MAIN_URL)
                .addConverterFactory(new ZumpaConverterFactory(new ZumpaSimpleParser()))
                .client(client)
                .build();

        ZumpaAPI zumpaAPI = retrofit.create(ZumpaAPI.class);
        Call<ZumpaResponse> call = zumpaAPI.login(new ZumpaLoginBody("testwp", "testwp"));
        Response<ZumpaResponse> execute = call.execute();
        //assertEquals(302, execute.raw().code());//success
    }

    @Test
    public void testSendResponse() throws IOException {
        final String[] sessionId = new String[1];
        OkHttpClient client = new OkHttpClient();
        HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor(new HttpLoggingInterceptor.Logger() {
            @Override
            public void log(String message) {
                System.out.println(message);
            }
        });

        Interceptor addCookiesInterceptor = new Interceptor() {
            @Override
            public com.squareup.okhttp.Response intercept(Chain chain) throws IOException {
                Request.Builder builder = chain.request().newBuilder();
                if (sessionId[0] != null) {
                    builder.addHeader("Cookie", "PHPSESSID=" + sessionId[0]);
                }
                return chain.proceed(builder.build());
            }
        };


        client.setFollowRedirects(false);
        interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
        client.interceptors().add(interceptor);
        client.interceptors().add(addCookiesInterceptor);
        client.interceptors().add(new Interceptor() {
            @Override
            public com.squareup.okhttp.Response intercept(Chain chain) throws IOException {
                Request request = chain.request();
                com.squareup.okhttp.Response response = chain.proceed(request);
                Headers headers = response.headers();
                String ssid = sessionId[0] = ParseUtils.Companion.extractPHPSessionId(headers);
                System.err.println("SessionId:" + ssid);
                return response;
            }
        });

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(ZR.Constants.ZUMPA_MAIN_URL)
                .addConverterFactory(new ZumpaConverterFactory(new ZumpaSimpleParser()))
                .client(client)
                .build();

        ZumpaAPI zumpaAPI = retrofit.create(ZumpaAPI.class);
        Call<ZumpaResponse> call = zumpaAPI.login(new ZumpaLoginBody("JtS", ""));
        Response<ZumpaResponse> execute = call.execute();
        assertEquals(302, execute.raw().code());//success

        ZumpaThreadBody body = new ZumpaThreadBody("JtSTest", "Houba", "TestBody2", "1658450");

        Call<ZumpaThreadResult> call2 = zumpaAPI.sendResponse("1658450", "1658450", body);
        Response<ZumpaThreadResult> execute1 = call2.execute();
        assertEquals(302, execute1.raw().code());//success
    }

    @Test
    @Ignore
    public void testSendThread() throws IOException {
        final String[] sessionId = new String[1];
        OkHttpClient client = new OkHttpClient();
        HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor(new HttpLoggingInterceptor.Logger() {
            @Override
            public void log(String message) {
                System.out.println(message);
            }
        });

        Interceptor addCookiesInterceptor = new Interceptor() {
            @Override
            public com.squareup.okhttp.Response intercept(Chain chain) throws IOException {
                Request.Builder builder = chain.request().newBuilder();
                if (sessionId[0] != null) {
                    builder.addHeader("Cookie", "PHPSESSID=" + sessionId[0]);
                }
                return chain.proceed(builder.build());
            }
        };


        client.setFollowRedirects(false);
        interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
        client.interceptors().add(interceptor);
        client.interceptors().add(addCookiesInterceptor);
        client.interceptors().add(new Interceptor() {
            @Override
            public com.squareup.okhttp.Response intercept(Chain chain) throws IOException {
                Request request = chain.request();
                com.squareup.okhttp.Response response = chain.proceed(request);
                Headers headers = response.headers();
                String ssid = sessionId[0] = ParseUtils.Companion.extractPHPSessionId(headers);
                System.err.println("SessionId:" + ssid);
                return response;
            }
        });

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(ZR.Constants.ZUMPA_MAIN_URL)
                .addConverterFactory(new ZumpaConverterFactory(new ZumpaSimpleParser()))
                .client(client)
                .build();

        ZumpaAPI zumpaAPI = retrofit.create(ZumpaAPI.class);
        Call<ZumpaResponse> call = zumpaAPI.login(new ZumpaLoginBody("JtS", ""));
        Response<ZumpaResponse> execute = call.execute();
        assertEquals(302, execute.raw().code());//success

        ZumpaThreadBody body = new ZumpaThreadBody("JtS", "HokusPokus", "Tralalala", null);

        Call<ZumpaThreadResult> call2 = zumpaAPI.sendThread(body);
        Response<ZumpaThreadResult> execute1 = call2.execute();
        assertEquals(302, execute1.raw().code());//success
    }
}