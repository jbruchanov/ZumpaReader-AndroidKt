# ZumpaReader → Kotlin Multiplatform

`ARCHITECTURE.md` is the current shape. This is the plan to get the non-UI half of the app off the
JVM. Phase 1 is the only phase specified to the line; later phases are scoped, not scripted.

**Guiding rule: phase 1 does not turn on the `kotlin("multiplatform")` plugin.** Every JVM-only
dependency is swapped for a KMP-capable one *inside the existing Android module*, so the app builds,
installs and passes tests after every single step. Only once the code is KMP-clean does the plugin go
on and the files move (phase 2). Introducing the plugin first means a long red build and a rewrite of
the parser, the network layer and the JSON layer all at once.

---

## Status

**Phases 1 and 2 are done.** `gson`, `kotson`, `jsoup` and `retrofit` are gone; the code is split
into `:shared` (KMP, android + jvm) and `:appAndroid`. 152 tests pass and the debug APK builds. What each
step turned out to involve is recorded inline below.

Still in `:appAndroid`, and correctly so for now:

- **The whole UI** — `androidx.compose` and `navigation3`, plus `arch/BaseViewModel` and
  `text/AnnotatedTextRenderer`, which are Compose-bound. That is phase 3.
- **Genuinely Android things** — camera capture, bitmap resizing and `ImageCacheRepository` (returns
  a `Bitmap`), notification channels, `MyFirebaseService`, the `Context` extensions in `ext/`, and
  `ParseUtils.MD5` (`MessageDigest`, used only by the Android-only image cache naming).
- okhttp, in two roles that are not portability problems: the Ktor engine (`ktor-client-okhttp`,
  swapped for Darwin on iOS) and Coil's own network layer.
- **R8 is off** (`minifyEnabled = false` on both build types). Nothing is obfuscated today, so the
  explicit `@SerialName` on every persisted and wire-facing property is insurance for when it is
  turned on rather than a fix for a current bug. kotlinx.serialization and Ktor both ship consumer
  ProGuard rules, so no hand-written keep rules were added.

**Not covered by tests: survey parsing.** Neither captured thread contains a survey, so
`parseSurvey`/`parseSurveyImpl`/`parseSurveyRow` went through the port unverified. Worth capturing a
fixture the next time a survey appears on the forum.

---

## Where the JVM lives today

| Dependency | Where | Replacement |
|---|---|---|
| `gson` 2.14.0 + `kotson` 2.5.0 | 4 call sites, 1 exclusion strategy | `kotlinx-serialization-json` — **already on the classpath** |
| `retrofit` 3.0.0 + `converter-gson` | `ZumpaAPI.kt`, 5 classes in `data/` | Ktor 3 client |
| `okhttp` 5.4.0 + `logging` + `urlconnection` | `Modules.kt`, `OversizedCookieInterceptor`, multipart upload | Ktor client — okhttp stays as the *Android engine only* |
| `jsoup` 1.23.1 | `ZumpaSimpleParser.java`, nothing else | `com.fleeksoft.ksoup:ksoup` |
| `java.text.SimpleDateFormat`, `java.util.Date` | `ZumpaSimpleParser`, `MainListScreen`, `SubListScreen` | `kotlinx-datetime` + `kotlin.time.Instant` |
| `java.nio.charset.Charset("ISO-8859-2")` | `ZumpaGenericResponse`, `HttpEncoding`, parser | hand-written Latin-2 table (see 1.1) |
| `java.net.URLEncoder` | `HttpEncoding.encodeHttp` | hand-written percent-encoder (see 1.1) |
| `java.net.CookieManager` / `CookiePolicy` / `URI` | `CookieRepository`, `Modules.kt` | Ktor `HttpCookies` + custom `CookiesStorage` |
| `java.util.regex.Pattern` | parser, `ParseUtils` | `kotlin.text.Regex` |
| `java.security.MessageDigest` | `ParseUtils.MD5` | okio `ByteString.md5()` |
| `java.io.File` / `FileOutputStream` | offline snapshot, image upload | kotlinx-io `SystemFileSystem` |
| `android.text.Html.fromHtml` | parser ×3, `ParseUtils`, `MyFirebaseService` | Ksoup (see 1.4) |
| `android.text.TextUtils` | parser ×2 | `isNullOrEmpty()` |
| `android.util.Patterns.WEB_URL` | `ParseUtils.linkPatterns` | own `Regex` |
| `R.drawable.emoji_*` inside the parser | `SmileRes.DATA` | `enum class Smiley` + mapping in the UI layer |
| `junit-jupiter`, `mockk` | all tests | `kotlin.test`, and `mokkery` or hand-written fakes |

Already multiplatform and staying: coroutines, kotlinx-serialization, Koin 4, Coil 3,
`androidx.annotation`, `androidx.lifecycle` 2.11, turbine.

**Out of scope by decision:** `ZumpaPrefs` / `SharedPreferences`. Storage stays Android-only for now;
`ZumpaPrefs` becomes an `expect class` (or an interface with an Android impl) in phase 2 and nothing
before that has to change.

**Staying Android-only permanently** (behind `expect`/`actual` in phase 2): Firebase messaging and
Crashlytics, `Bitmap`/`BitmapFactory` in `ParseUtils.resizeImageIfNecessary` and
`ImageCacheRepository`, notification channels, `Uri`, `Context`.

---

## Phase 0 — the safety net (done)

`ZumpaSimpleParser` is 588 lines of HTML scraping against a legacy forum and it has **zero tests**.
Phase 1 rewrites it in Kotlin, swaps its HTML engine, its charset handling, its date parsing and its
regex engine. Without a golden-file test that refactor cannot be verified at all.

Four pages were captured from the live forum on 2026-08-08 into `app/src/test/resources/`, **as the
bytes the server sent** — ISO-8859-2, not re-encoded — which makes every parser test an end-to-end
check of the charset work too:

| Fixture | What it pins |
|---|---|
| `mainpage_default.html` | 35 rows, the `dd. MM. yyyy HH:mm:ss` date branch, one nav link |
| `mainpage_lastauthor.html` | the same page under the `newdate` cookie: the `HH:mm` branch plus a last-author name in the sixth column |
| `thread_page.html` | 4 posts, `Datum:` dates, `reply2('@name:` author scraping |
| `thread_survey.html` | 47 posts — exercises the table-walking loop at scale |

`ZumpaSimpleParserTest` (21 tests) asserts ids, subjects, authors, item counts, urls, flags and
`time` — the last one converted back to a `LocalDateTime` rather than compared as a raw epoch, so it
is timezone-independent while still catching a parse regression.

**The expectations were extracted from the raw HTML independently of the parser**, with shell tooling,
not recorded from its output. A test that agrees with the implementation by construction would have
proven nothing here.

Two notes on what could not be done as planned:

- **No baseline could be captured from the old parser.** It called `android.text.Html` and
  `TextUtils`, which throw outside an Android runtime, so the pre-port implementation was literally
  untestable on the JVM without adding Robolectric. Hence the independent extraction above.
- **No `offline.json` or `/zumpa` fixture.** The web service (`zumpaws.scurab.com:8104`) answers with
  an empty reply, so the JSON tests use hand-built payloads whose field names come from the gson
  reader they replace — authoritative for the shape, if not a live sample.

This step earned its keep immediately: see 1.3 and 1.4.

---

## Phase 1 — swap the dependencies

Ordered by dependency: 1.1 unblocks 1.4 and 1.5, 1.2 is independent, 1.3 rides along with 1.4.

### 1.1 — Latin-2 codec and form encoding

The forum is `ISO-8859-2` (`ZR.Constants.ENCODING`). This is the sharpest edge in the whole
migration: **Kotlin/Native and Kotlin/JS have no general charset support, and Ktor's
`Charset.forName` throws for anything but UTF-8 on native.** There is no library shortcut. Latin-2 is
a fixed 256-entry table, so write it.

New `util/Latin2.kt`:

```kotlin
/** ISO-8859-2. Index = unsigned byte value, value = the code point it maps to. */
private val LATIN2_DECODE: CharArray = charArrayOf(/* 0x00..0x7F identity, then the 128 Latin-2 chars */)
private val LATIN2_ENCODE: Map<Char, Byte> = /* inverse of the above */

fun ByteArray.decodeLatin2(): String
fun Char.latin2ByteOrNull(): Byte?
fun String.canEncodeLatin2(): Boolean

/** `URLEncoder.encode(s, "ISO-8859-2")`: space -> '+', [A-Za-z0-9*-._] literal, else %XX of the Latin-2 byte. */
fun String.percentEncodeLatin2(): String
```

Then:

- **`HttpEncoding.kt`** — `zumpaCharset.newEncoder().canEncode(x)` → `x.canEncodeLatin2()`;
  `URLEncoder.encode(...)` → `percentEncodeLatin2()`. `Character.charCount(cp)` and `codePointAt`
  have no common equivalent — add a small `codePointAt`/`charCount` pair over
  `Char.isHighSurrogate()` in the same file. The `&#<decimal>;` fallback for un-encodable code points
  is behaviour the forum depends on, so keep the logic byte for byte.
- **`ZumpaModel.kt`** — `asString()` → `data.decodeLatin2()`; `asUTFString()` → `data.decodeToString()`.
  Drop `import java.nio.charset.Charset` and the unused `import ...R`.
- **`ZR`** — `ENCODING` stops being a charset name and becomes irrelevant; leave the constant, it is
  still the value sent to the server in headers.

`HttpEncodingTest` is the regression net here and must pass unchanged. Add a round-trip test against
known Latin-2 byte sequences (`ěščřžýáíé` and a couple of emoji) so the table itself is verified.

### 1.2 — gson + kotson → kotlinx.serialization

No new plugin: `org.jetbrains.kotlin.plugin.serialization` is already applied and
`kotlinx-serialization-json` is already in the `compose` bundle (move it to its own line — it is not
a Compose dependency).

There are two distinct JSON shapes, currently both handled by ad-hoc `JsonObject` poking. Give each
an explicit DTO:

**A. the offline snapshot on disk** (lowercase keys, written by `OfflineDownloadUseCase`, read by
`OfflineDataRepository` — the two must agree):

```kotlin
@Serializable
data class OfflineThreadDto(
    val id: String, val subject: String, val author: String = "",
    val contentUrl: String = "", val time: Long = 0,
    val lastAuthor: String? = null,
    val offlineItems: List<OfflineThreadItemDto>? = null,
    val isFavorite: Boolean = false,
    val hasResponseForYou: Boolean = false,
)
```

**B. the `/zumpa` web-service response** (PascalCase, read only):

```kotlin
@Serializable class WsResponse(@SerialName("Context") val context: WsContext)
@Serializable class WsContext(@SerialName("Items") val items: List<WsThreadDto>)
@Serializable class WsThreadDto(
    @SerialName("ID") val id: String, @SerialName("Subject") val subject: String,
    @SerialName("Time") val time: Long, @SerialName("Author") val author: String,
    @SerialName("HasRespondForYou") val hasResponseForYou: Boolean,
    @SerialName("Items") val items: List<WsItemDto>,
)
```

with `fun WsThreadDto.toDomain(): ZumpaThread` / `fun OfflineThreadDto.toDomain()` and the reverse.
Note the existing `AuthorFake` bug in `OfflineDownloadUseCase` (it reads `AuthorReal` in both
branches) — reproduce or fix deliberately, don't change it by accident.

Then:

- `ZumpaThread.thread(JsonObject)`, `JsonArray.asItems()`, `JsonObject.asItem()` — **delete**, the
  DTO replaces them. Same for the private `asZumpaThread()` / `asZumpaThreadItems()` in
  `OfflineDownloadUseCase`.
- `ZumpaReadStateRepository` — `@Serializable` on `ZumpaReadState`; `gson.fromJson(json, TypeToken<TreeMap<…>>)`
  → `json.decodeFromString<Map<String, ZumpaReadState>>(…)` into the existing `TreeMap`;
  `gson.toJson(toStore)` → `json.encodeToString(toStore as Map<String, ZumpaReadState>)`. The
  `subMap` trimming logic stays verbatim.
- `OfflineDataRepository.loadFromDisk` — the `GsonBuilder` + `registerTypeAdapter` + `JsonReader`
  block collapses to `json.decodeFromString<Map<String, OfflineThreadDto>>(file.readText())`.
- `gson/GsonExclude.kt` — **delete the package**. It exists only because gson reflects over fields it
  should not see (`date`, `idLong$delegate`). kotlinx.serialization only sees declared properties, so
  the whole mechanism disappears. While there: `ZumpaThread.date` and `ZumpaThreadItem.date` are
  **dead** — nothing reads either. Delete them and the `java.util.*` import with them; that removes
  two thirds of step 1.3.
- `Modules.kt` — `single { Gson() }` → `single { Json { ignoreUnknownKeys = true; explicitNulls = false } }`.
  Update `ModulesTest`.
- Remove `libs.gson`, `libs.kotson`, `libs.retrofit.converter.gson` from `libs.versions.toml`.

**On-disk compatibility: kept, not broken.** The plan originally recommended accepting the format
break, but matching gson's emitted names turned out to cost exactly one `@SerialName("_items")` — the
private backing field behind `ZumpaThread.items` — so existing `offline.json` snapshots still load.
`JsonMappingTest` has a `a snapshot written by the gson version still loads` case built from those
names. `_items` is written and then ignored on read, which is what the gson reader did too: the count
has always been recomputed from `offlineItems`.

**Every serialised property names itself explicitly.** Three tests assert the key sets as literal
strings, so a property rename — by hand or by R8 — fails the build rather than quietly orphaning a
saved snapshot. Note that kotlinx.serialization omits properties equal to their default, so those
tests set every value off its default; they are about the *names*, not about which keys get emitted.

**One bug deliberately preserved.** `OfflineDownloadUseCase` read `AuthorReal` in *both* branches of
its `has("AuthorFake")` check, so the fake author name has never been used. Reproducing it keeps this
a dependency swap rather than a silent change to displayed names; it is commented at
`ZumpaWsItemDto.toDomain` and asserted in the test.

### 1.3 — datetime → kotlinx-datetime

`kotlinx-datetime` 0.8.0. Note 0.7.0 moved `Instant` and `Clock` into the stdlib, so the imports are
`kotlin.time.Instant` + `kotlinx.datetime.{TimeZone, LocalDateTime, LocalTime, format}`.

After 1.2 deletes the two dead `Date` properties, three formatting sites and one parsing site remain.

**Formatting** — `MainListScreen.kt:346-347`, `SubListScreen.kt:419`:

```kotlin
// "dd.MM. HH:mm.ss" — note the '.' before seconds, it is not a typo, reproduce it
private val dateFormat = LocalDateTime.Format {
    day(); char('.'); monthNumber(); chars(". ")
    hour(); char(':'); minute(); char('.'); second()
}
private val shortDateFormat = LocalDateTime.Format { hour(); char(':'); minute() }

private fun Long.formatted(short: Boolean) =
    Instant.fromEpochMilliseconds(this)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .format(if (short) shortDateFormat else dateFormat)
```

`SimpleDateFormat` used the default timezone, so `currentSystemDefault()` is behaviour-preserving.
(`day()` was `dayOfMonth()` before 0.7 — use whichever the resolved version exposes.)

**Parsing** — `ZumpaSimpleParser.safeParse`, formats `"dd. MM. yyyy HH:mm:ss"` and `"HH:mm"`. The
short one currently yields *1970-01-01 at that time-of-day in the default zone*, because that is what
`SimpleDateFormat("HH:mm").parse()` does. Reproduce exactly:

```kotlin
private fun parseFullDate(value: String): Long = runCatching {
    LocalDateTime.parse(value, fullFormat).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
}.getOrDefault(0L)

private fun parseTimeOnly(value: String): Long = runCatching {
    LocalDate(1970, 1, 1).atTime(LocalTime.parse(value, timeFormat))
        .toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
}.getOrDefault(0L)
```

**This is where phase 0 paid for itself.** `SimpleDateFormat` stopped parsing at the end of its
pattern and ignored whatever followed; `kotlinx-datetime` insists the whole string is consumed. The
dates inside a post are lifted out of Ksoup's *pretty-printed* `html()`, which leaves a trailing
newline in the capture, so the first run of the fixture tests came back with **every post in every
thread dated 1970** — a silent, total regression that no compiler would have caught and that a
casual look at the app might not have either. Both parse helpers now `trim()` first.

(jsoup pretty-printed too, so this is not a Ksoup behaviour change — it is `SimpleDateFormat`'s
leniency having quietly covered for it all along.)

### 1.4 — jsoup → Ksoup, and the parser to Kotlin

`ZumpaSimpleParser.java` is the *only* jsoup consumer and simultaneously the app's densest knot of
JVM and Android API. Convert it to Kotlin in the same pass — half of it is `Pattern`, `Html`,
`TextUtils` and `SimpleDateFormat`, all of which have to go anyway.

`com.fleeksoft.ksoup:ksoup` 0.2.6 is a direct port of jsoup, and the API surface actually used is
tiny — 17 `getElementsByTag`, 8 `text()`, 6 `attr`, 4 `html()`, 3 `hasAttr`, 1 `select`, `first()`.
All of it maps 1:1; `Jsoup` → `Ksoup` is the whole rename.

- **Only the core `ksoup` artifact is needed.** The two stream overloads
  (`Jsoup.parse(stream, "ISO-8859-2", "")`) become `Ksoup.parse(bytes.decodeLatin2())` using 1.1, so
  no `ksoup-kotlinx` / `ksoup-okio` / `ksoup-network` engine module and no charset dependency.
  This also means the parser stops taking an `InputStream` and starts taking a `String` — which is
  what the Ktor layer in 1.5 wants anyway.
- `Html.fromHtml(x).toString()` (parser ×3, `ParseUtils.parseLink`, `MyFirebaseService` ×2) is only
  ever used to decode HTML entities in a short text. Add one helper and route all six through it:
  `fun String.decodeHtmlEntities(): String`, implemented with Ksoup's `Entities` unescape if that API
  is exposed in 0.2.6, otherwise `Ksoup.parseBodyFragment(this).wholeText()`. Do **not** use
  `.text()` — it normalises whitespace, `Html.fromHtml` does not. Cover this with a test on the
  author-name fixtures; the double `fromHtml` in `MyFirebaseService:77-78` is intentional
  double-encoding and must stay double.
- `java.util.regex.Pattern` → `Regex(..., RegexOption.IGNORE_CASE)`; `matcher(s).find()/group(1)` →
  `regex.find(s)?.groupValues?.get(1)`.
- `TextUtils.isEmpty(x)` → `x.isNullOrEmpty()`.
- `androidx.core.util.Pair` → Kotlin `Pair`.
- `SmileRes.DATA: Map<Int /*R.drawable*/, Pattern>` → `enum class Smiley` + `Map<Smiley, Regex>`.
  `AnnotatedTextRenderer:123` does the `R.drawable` lookup instead. This is the parser's last Android
  import.
- The three empty `dispatchParsing*` methods are dead — delete.

Ksoup's API turned out to be a drop-in for everything the parser used; the whole port compiled
against it first try. Two things worth recording:

- **`text()` normalises `&nbsp;` to a plain space, `wholeText()` does not.** The list's last-author
  column depends on the first (it splits the cell on a space that is a `&nbsp;` in the markup) and
  `getAuthorName` depends on the second (it searches a raw text node for ` `). Ksoup matches
  jsoup on both, which is why `isShowLastUser` still yields `LSC` rather than the whole cell.
- **Most of `ParseUtils` was dead.** `parseLink`, `hasPHPSessionId`, `extractPHPSessionId`,
  `extractSessionId` and `resizeImageIfNecessary` had no callers, and deleting them removed the last
  uses of `android.text.Html`, `android.util.Patterns`, `Bitmap`/`BitmapFactory` and okhttp's
  `Headers` from that file in one go. `ZR.Constants.USER_AGENT` is unused too — the app has never
  actually sent it, so it was left alone rather than newly introduced.

### 1.5 — retrofit + okhttp → Ktor

**Plain Ktor `HttpClient`, not Ktorfit.** Ktorfit 2.7.5 would keep the annotations on `ZumpaAPI`, but
it buys little here: there are 12 endpoints, every request body is a hand-built form string
(`ZumpaBody.toHttpPostString()`), every response converter is really "decode Latin-2, run the HTML
parser", and four endpoints use `Call<T>` purely to read a 302 and its headers. You would write all
the same converters plus add KSP codegen. A plain client makes the redirect and header handling
direct, and deletes five converter classes instead of porting them.

Client (`Modules.kt`, replacing `buildHttpClient`):

```kotlin
HttpClient(engine) {                       // expect fun zumpaEngine(): HttpClientEngineFactory<*>
    followRedirects = false                // a 302 IS the success signal — see Calls.kt
    expectSuccess = false                  // read status explicitly instead of catching
    install(HttpTimeout) { connectTimeoutMillis = 5_000; requestTimeoutMillis = 25_000; socketTimeoutMillis = 25_000 }
    install(HttpCookies) { storage = ZumpaCookiesStorage(prefs) }
    if (BuildConfig.DEBUG) install(Logging) { level = LogLevel.ALL }
    defaultRequest { header("Cache-Control", "max-age=0"); header(HttpHeaders.UserAgent, ZR.USER_AGENT) }
}.also { it.plugin(HttpSend).intercept(oversizedCookieRetry(cookies)) }
```

Mapping, piece by piece:

| Now | Ktor |
|---|---|
| `Retrofit.Builder().baseUrl(...)` ×3 | three thin classes (`ZumpaClient`, `ZumpaWsClient`, `ZumpaPhpClient`) each holding the shared `HttpClient` + its base URL |
| `ZumpaConverterFactory` + 4 converters | gone. Each call does `client.get(...).bodyAsBytes().decodeLatin2()` then `parser.parseMainPage(html)` |
| `ZumpaBody` request converter | `setBody(body.toHttpPostString())` + `contentType(FormUrlEncoded)` |
| `Call<ZumpaGenericResponse>` ×4 | `suspend fun` returning a small `ZumpaResponse(status: HttpStatusCode, headers: Headers, bytes: ByteArray)` |
| `JavaNetCookieJar(CookieManager)` + `CookieRepository.reset()` | `ZumpaCookiesStorage` modelled on `AcceptAllCookiesStorage`, with the same `reset()` — drop everything, re-seed from `prefs.cookies`. `CookieRepository` keeps its name and its doc comment, the `java.net` types go |
| `OversizedCookieInterceptor` (502 → reset → retry once) | an `HttpSend` interceptor. `HttpSend` sits *above* cookie rendering, which is exactly the "application interceptor" position the current doc comment explains it needs |
| `addNetworkInterceptor { ..._ts=now... }` | same `HttpSend` interceptor, or its own tiny plugin |
| `HttpLoggingInterceptor(BODY)` | `Logging { level = LogLevel.ALL }` |
| `retrofit2.HttpException.code() == 302` in `Calls.kt` | `response.status == HttpStatusCode.Found`. With `expectSuccess = false` the whole `ignoringZumpaRedirect` try/catch becomes a status check — delete it |
| `AuthRepository.login(...).execute().code() == 302` | same |
| `ParseUtils.extractCookies/extractPHPSessionId(okhttp3.Headers)` | `response.headers.getAll(HttpHeaders.SetCookie)` / `response.setCookie()` |
| `MultipartBody.Part.createFormData` in `ImageUploadRepository` | `MultiPartFormDataContent(formData { append("image", bytes, Headers.build { … }); append("name", "Submit") })` |
| `withContext(Dispatchers.IO)` around calls | keep for now; becomes `Dispatchers.Default` in phase 2 (`IO` is JVM-only) |

`ZumpaAPI` stays as an interface — it is the online/offline switch and `ZumpaOfflineApi` implements
it. Strip the retrofit annotations, make the four `Call<T>` methods `suspend`, and the offline impl
needs only its signatures updated.

Tests: `OversizedCookieInterceptorTest` and `ZumpaThreadRepositoryTest` get rewritten against Ktor's
`MockEngine`. That is a straight improvement — `MockEngine` is multiplatform and far less ceremony
than the okhttp `Response` builders.

Keep `okhttp` in the catalog as the **Android engine** (`ktor-client-okhttp`); it is no longer a
portability problem because no common code sees it. `okhttp-urlconnection` and
`retrofit-converter-gson` are deleted.

### 1.6 — the remaining leaves

Small, independent, no ordering constraints. Can trail 1.1–1.5 or land alongside.

- `ParseUtils.MD5` → okio `value.encodeUtf8().md5().hex()`. One caller,
  `CopyFromResourcesTask:26`. (Alternative if you would rather not add okio: `korlibs-crypto`.)
- `java.io.File`/`FileOutputStream`/`FileInputStream` in `OfflineDataRepository`,
  `OfflineDownloadUseCase`, `ImageUploadRepository` → kotlinx-io `SystemFileSystem` + `Path`. The
  *directory* (`context.getExternalFilesDir`) stays Android and becomes an injected `Path` in phase 2.
- `android.util.Patterns.WEB_URL` in `ParseUtils.linkPatterns` → an explicit `Regex`. Copy the
  pattern into the codebase with a comment; it is the only way to keep the current matching.
- `ParseUtils` splits: the regex/cookie/MD5 half is portable, `resizeImageIfNecessary` is not. Split
  the file now (`ParseUtils` / `BitmapUtils`) so phase 2 is a file move rather than a surgery.
- Tests: `junit-jupiter` → `kotlin.test`; `mockk` is JVM-only → `mokkery` (KSP, KMP) or hand-written
  fakes. Do this last, and only for the tests that move to `commonTest` in phase 2.

### `libs.versions.toml` after phase 1

```diff
 [versions]
-gson = "2.14.0"
-kotson = "2.5.0"
-jsoup = "1.23.1"
-retrofit = "3.0.0"
+ksoup = "0.2.6"
+ktor = "3.5.0"
+kotlinx-datetime = "0.8.0"
+kotlinx-io = "0.9.0"          # check current
+okio = "3.11.0"               # check current; only for md5
 okhttp = "5.4.0"              # now only the ktor android engine

 [bundles]
-retrofit = ["retrofit-base", "retrofit-converter-gson"]
-okhttp = ["okhttp-base", "okhttp-logging", "okhttp-urlconnection"]
+ktor = ["ktor-client-core", "ktor-client-logging", "ktor-client-content-negotiation", "ktor-serialization-kotlinx-json"]
```

with `ktor-client-okhttp` as an Android-only implementation dependency and `ktor-client-mock` for
tests. Move `kotlinx-serialization-json` out of the `compose` bundle into its own line.

### Definition of done for phase 1

- ✅ `libs.versions.toml` contains no `gson`, `kotson`, `jsoup` or `retrofit` entry.
- ✅ `app/src/main/java/` is deleted — the app is Kotlin-only.
- ✅ 131 unit tests pass; `assembleDebug` succeeds.
- ✅ Remaining `java.*` uses are `TreeMap` ×2, `File`/`FileOutputStream` ×9 and `MessageDigest` ×1,
  all accounted for under **Status** above.
- ✅ **Exercised on an emulator** (Pixel 9a, API 36, clean install) after the phase-2 split: the main
  list loads over Ktor and parses with the right diacritics, settings write and read back across a
  process restart through `SharedPreferencesStore`, `KEY_READ_STATES` is written as JSON by the
  `TreeMap`-replacement code, and an `offline.json` pushed onto the device is read by
  `OfflineDataRepository` through kotlinx-io with the item counts derived correctly.
- ⬜ **Still not exercised: posting.** Three things remain only as good as their unit tests, because
  they need a real session: the 302-as-success path (`followRedirects = false` plus per-request
  `expectSuccess = false`), the ISO-8859-2 form encoding on a real POST, and the multipart image
  upload, which changed from a streamed `File` body to a `ByteArray` one and again to kotlinx-io.
  Log in and post once before shipping.

---

## Phase 2 — turn on the plugin, extract `:shared` (done)

The project is now `:shared` + `:appAndroid` + `:appJvm`. `:shared` targets `androidTarget()` and `jvm()`; 31 files are in `commonMain`, one each in
`androidMain` and `jvmMain`, and 61 remain in `:appAndroid`. 152 tests pass.

**The `jvm()` target ships nothing.** Nothing consumes it. It is a compile-time guard: `commonMain`
has no Android on its classpath under that target, so an `android.*` import that sneaks in fails the
build rather than quietly making the module un-portable. `commonMain` currently has **zero**
`android.*`, `androidx.*`, `java.*` or `javax.*` imports, and that is checked by the compiler rather
than by grep.

### What the build needed

- **Groovy → `.kts`**, as planned. `app/tools.gradle`'s two helpers (`getDate`, `gitSha`) are now
  functions in `app/build.gradle.kts`, with `gitSha` on `providers.exec` so it stays
  configuration-cache friendly.
- **`com.android.library` is rejected by AGP 9 alongside the multiplatform plugin.** The replacement
  is `com.android.kotlin.multiplatform.library`, configured through `kotlin { android { … } }`
  rather than a top-level `android {}` block. Its `androidLibrary { }` alias is already deprecated.
- `platform(...)` is not in scope inside a KMP source-set `dependencies {}` block —
  `project.dependencies.platform(...)` is.

### The seams

Interfaces, not `expect`/`actual`: each of these has to be substitutable in a test as well as per
platform, and an `expect class` is neither.

| Seam | commonMain | Android | jvm |
|---|---|---|---|
| `KeyValueStore` | `ZumpaPrefs` reads/writes through it, `changes: Flow<String?>` replaces the preference listener | `SharedPreferencesStore` — same file and keys, so installs keep their settings | `InMemoryKeyValueStore` |
| `PushTokenProvider` | `AuthRepository` asks for a token | `FirebasePushTokenProvider` in `:appAndroid` | `NoPushTokenProvider` |
| `ImagePrefetcher` | `OfflineDownloadUseCase` warms the cache | `CoilImagePrefetcher` in `:appAndroid` | `NoImagePrefetcher` |
| file paths | `OfflineDataRepository` takes a `String` path, I/O via kotlinx-io | `:appAndroid` computes it from `getExternalFilesDir` | — |

### `TreeMap`, resolved rather than deferred

`java.util` is invisible in `commonMain` whatever the targets, so this stopped being a "later" item.

- `ZumpaThreadRepositoryImpl` → `LinkedHashMap`. Nothing depended on the sorting: the list screen
  sorts by `idLong` itself, and `lastThread()` was the only reader of the order. `keys.maxOrNull()`
  reproduces `lastEntry()` exactly, including its quirk of comparing ids as *strings*.
- `ZumpaReadStateRepository.persist()` → `descendingKeySet()` and the half-open `subMap(first, last)`
  written out longhand, with the "excludes the newest entry" behaviour preserved.

### Tests

`commonTest` runs on both targets; `jvmTest` holds the ones that need JVM-only *tooling* — `mockk`,
and loading the captured html fixtures off the classpath. `Latin2Test` and `HttpEncodingTest` stay
there deliberately: their value is comparing against the JDK's own charset and `URLDecoder` as an
oracle, which is not something to reimplement in common code.

---

## Phase 2 — the original plan

1. Convert the Groovy build scripts to `.kts` first. `refreshVersions` in `settings.gradle` will need
   re-checking against the KMP plugin.
2. New module `:shared` with `androidTarget()`, `iosArm64()`, `iosSimulatorArm64()`.
3. Move, in this order (each step compiles):
   `model/` → `util/` (portable half) → `text/` → `data/` → `arch/` → `repository/` → `usecase/` →
   `reader/`. `ui/` and `di/` stay in `:appAndroid` for now.
4. `expect`/`actual` boundaries to declare:
   - `ZumpaPrefs` — Android `SharedPreferences`, iOS TBD (out of scope, so: `expect class` with the
     Android actual only, and no iOS target for the settings-touching source set until later)
   - `zumpaEngine(): HttpClientEngineFactory<*>` — OkHttp / Darwin
   - `offlineSnapshotPath(): Path`
   - `PushRegistrar` — Firebase on Android, no-op elsewhere
   - `CrashReporter` — Crashlytics on Android
   - `imageResizer` — `Bitmap` on Android
5. `Dispatchers.IO` → `Dispatchers.Default` (or an injected dispatcher) in everything that moved.
6. Tests move to `commonTest`, run on both targets. This is where the `mockk` → `mokkery` swap
   actually becomes necessary.

## Phase 3 — Compose Multiplatform

`androidx.compose.*` → `org.jetbrains.compose`. Independently: `androidx.navigation3` — verify its
KMP artifacts before committing, this is the one dependency in the current stack whose multiplatform
story needs checking rather than assuming. Coil 3 is already multiplatform. Resources move to
`compose-resources`. `google-material` is Android-XML-theme-only and is probably deletable outright.

## Phase 4 — `:iosApp`

Only worth starting once phases 2 and 3 are done. The open question left over from phase 1 is
storage: `ZumpaPrefs` needs an iOS actual (`NSUserDefaults`, or `multiplatform-settings`) and the
cookie persistence rides on it.

---

## Risks, in hindsight

1. **The parser rewrite** (1.4) — as expected, the one that bit. The strict-date regression would
   have shipped without the fixtures. Ksoup itself was a non-event.
2. **ISO-8859-2** (1.1) — landed cleanly, and cheaply verified: while the tests still run on the JVM,
   the JDK's own charset is available as an oracle, so `Latin2Test` checks all 256 bytes and every
   `percentEncodeLatin2` case against `URLEncoder` rather than against a second copy of the table.
   Those assertions have to become fixed byte vectors when the code leaves the JVM.
3. **302-as-success** (1.5) — covered by `MockEngine` tests for both the post path and login, but
   only a device proves it end to end. See the definition of done.
4. **Offline snapshot format** (1.2) — resolved by keeping compatibility rather than breaking it.
5. **`androidx.navigation3` on KMP** (phase 3) — still unverified. Check early even though it lands
   late; if it is Android-only the phase-3 plan changes shape.

One thing the plan got wrong: it assumed a baseline could be recorded from the old parser before
replacing it. It could not — the old parser needed an Android runtime. Deriving the expectations from
the raw HTML instead was the stronger option anyway, and is worth reaching for first next time.

## Sources

- [fleeksoft/ksoup](https://github.com/fleeksoft/ksoup) ·
  [ksoup on Maven Central](https://central.sonatype.com/artifact/com.fleeksoft.ksoup/ksoup)
- [Kotlin/kotlinx-datetime releases](https://github.com/Kotlin/kotlinx-datetime/releases)
- [Ktor 3.4.0 release notes](https://blog.jetbrains.com/kotlin/2026/01/ktor-3-4-0-is-now-available/) ·
  [Ktor client supported platforms](https://ktor.io/docs/client-supported-platforms.html)
- [Ktorfit](https://foso.github.io/Ktorfit/) (evaluated, not chosen — see 1.5)
