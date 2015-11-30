package com.scurab.android.zumpareader;

import android.graphics.Color;

public final class ZR {
    public static final class Constants {
        public static final boolean DEBUG = true;
        public static final String ZUMPA_MAIN_URL = "http://portal2.dkm.cz";
        public static final String ZUMPA_LOGIN_URL = "http://portal2.dkm.cz/login.php";
        public static final String PHPSESSID = "PHPSESSID";
        public static final String SETCOOKIE_HEADER = "Set-Cookie";
        public static final String ZUMPA_POST_URL = ZUMPA_MAIN_URL + "post.php";
        public static final String ZUMPA_LOGOUT = "http://portal2.dkm.cz/logout.php?local=1";
        public static final String ENCODING = "ISO-8859-2";

        public static final class WebForm {
            public static final String LOGIN_FORM_NAME = "nick";
            public static final String LOGIN_FORM_PASSWORD = "pass";
            public static final String LOGIN_FORM_TIMELIMIT = "rem";
            public static final String LOGIN_FORM_TIMELIMIT_VALUE = "5"; // forever
            public static final String LOGIN_FORM_BUTTON = "login";
            // public static final String ZUMPA_LOGIN_FORM_BUTTON_VALUE =
            // "P%F8ihl%E1sit";
            public static final String LOGIN_FORM_BUTTON_VALUE = "Přihlásit";

            // t a f p
            public static final String POST_ID1 = "t";
            public static final String POST_ID2 = "p";
            public static final String POST_TYPE = "a";
            public static final String POST_TYPE_VALUE = "post";
            public static final String POST_SOMETHING = "f";
            public static final String POST_SOMETHING_VALUE = "2";
            public static final String POST_USERNAME = "author";
            public static final String POST_SUBJECT = "subject";
            public static final String POST_BODY = "body";
            public static final String POST_BUTTON = "post";
            public static final String POST_BUTTON_VALUE = " Odeslat";// space
            // at the
            // beginning!
            public static final String RATE_THREADID = "threadid";
            public static final String RATE_TYPE = "typ";
            public static final String RATE_TYPE_FAVOURITE = "F";
            public static final String RATE_TYPE_IGNORE = "I";
            public static final String RATE_TYPE_SURVEY = "A";
            public static final String RATE_SURVEY_ID = "a";
            public static final String RATE_SURVEY_ITEM = "v";
            public static final String POST_SURVEY_QUESTION = "otazka";
            public static final String POST_SURVEY_ANSWER = "o%s"; // o1 - o6
            public static final String POST_SURVEY_ANKETA = "anketa";
            public static final String POST_SURVEY_ANKETA_VALUE = "1";
        }
    }
}
