# R8 is on for release - see `isMinifyEnabled` in build.gradle.kts. This file is deliberately short,
# because almost everything this app needs is already brought in by the libraries themselves:
#
# - **kotlinx.serialization** ships R8 rules (`META-INF/com.android.tools/r8/`) that keep the
#   generated `$serializer` for every `@Serializable`. That is what the Navigation 3 keys and the
#   offline snapshot depend on, and it is why the DTOs name every key with `@SerialName` - the
#   property names *are* renamed by R8, the json keys must not be.
# - **Compose**, **Firebase** and **Coil** ship their own consumer rules.
# - **Koin** needs none: definitions are lambdas that call constructors directly, and a definition
#   is keyed on the `KClass` object rather than on its name, so obfuscation cannot break a lookup.
# - **Ktor** engines are constructed explicitly (`HttpClient(OkHttp.create())`), not discovered
#   through a `ServiceLoader`, so the engine does not need keeping either.

# Crashlytics reports are unreadable without these; its gradle plugin uploads the mapping file.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
