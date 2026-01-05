./gradlew -c settings.ohos.gradle.kts :kuiklySqlite:publish
./gradlew :kuiklySqlite-compiler:publish
git subtree push --prefix maven-repo origin gh-pages