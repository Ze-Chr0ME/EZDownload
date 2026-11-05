# Native edition dependencies

No Python, yt-dlp, Node.js, or FFmpeg is used or bundled in this edition.

- NewPipe Extractor v0.26.5: GPL-3.0-or-later. https://github.com/TeamNewPipe/NewPipeExtractor
- NewPipe nanojson: Apache-2.0. https://github.com/TeamNewPipe/nanojson
- Rhino 1.8.1 / rhino-engine: MPL-2.0 and bundled notices. Interprets the YouTube player code as Java bytecode; no native JavaScript runtime is shipped. https://github.com/mozilla/rhino
- OkHttp 4.12.0 / Okio: Apache-2.0. https://github.com/square/okhttp
- Gson 2.13.2: Apache-2.0. https://github.com/google/gson
- Jsoup 1.22.2: MIT. https://github.com/jhy/jsoup
- mp4parser 1.9.56: Apache-2.0; includes AspectJ runtime and SLF4J dependencies under their own licenses. https://github.com/sannies/mp4parser
- Protocol Buffers Java Lite: BSD-3-Clause. https://github.com/protocolbuffers/protobuf
- Kotlin standard library: Apache-2.0. https://github.com/JetBrains/kotlin
- JNA 5.17.0 (Windows desktop only): LGPL-2.1-or-later / Apache-2.0 dual license. Calls the Windows shell API for Unicode-safe file selection. https://github.com/java-native-access/jna
- Android desugar JDK libraries: upstream notices are retained in the APK. https://github.com/google/desugar_jdk_libs
- Windows trimmed Java runtime: Eclipse Temurin/OpenJDK 21, GPL-2.0 with Classpath exception and bundled third-party licenses. Notices reside under `runtime/legal`. https://adoptium.net/

This application is distributed under GPL-3.0-or-later to comply with NewPipe Extractor. The Windows zip includes application source and build scripts; the same source builds Android. Preserve LICENSE, upstream jar/APK notices and runtime legal notices when redistributing. Maven/Gradle metadata pins the external source versions; provide corresponding source for modified dependencies if you make modifications to those libraries. Dependencies here are used without source changes.
