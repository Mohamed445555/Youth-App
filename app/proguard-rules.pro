# Keep the JS bridge classes/methods reachable from WebView JS.
-keepclassmembers class com.registry.app.SqliteBridge {
    public *;
}
-keepclassmembers class com.registry.app.FileBridge {
    public *;
}
