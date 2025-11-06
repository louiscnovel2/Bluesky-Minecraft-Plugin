package com.example.blueskyplugin;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import java.io.FileWriter;
import java.io.FileReader;
import java.util.Collections;
import java.util.Arrays;
import java.util.stream.Collectors;

public class BlueskyPlugin extends JavaPlugin {
    private Map<UUID, String> userTokens = new HashMap<>();
    private Map<UUID, String> userHandles = new HashMap<>();
    // ユーザーの選択言語を保存 ("japanese" or "english")
    private Map<UUID, String> userLang = new HashMap<>();
    private Map<UUID, List<String>> userFeeds = new HashMap<>();

    // ローカライズ用メッセージ辞書
    private final Map<String, Map<String, String>> messages = new HashMap<>();

    @Override
    public void onEnable() {
        getLogger().info("BlueskyPlugin enabled");
        
        // 保存されたデータを読み込む
        loadData();

        // メッセージ辞書初期化
        initMessages();
        
        // コマンドのTab補完を登録
        getCommand("bsky").setTabCompleter((sender, command, alias, args) -> {
            List<String> completions = new ArrayList<>();
            if (!(sender instanceof Player)) {
                return completions;
            }
            
            if (args.length == 1) {
                // 最初の引数の候補
                String[] commands = {"login", "logout", "post", "tl", "lang", "feed"};
                for (String cmd : commands) {
                    if (cmd.startsWith(args[0].toLowerCase())) {
                        completions.add(cmd);
                    }
                }
            }
            
            return completions;
        });
    }

    @Override
    public void onDisable() {
        // データを保存
        saveData();
        getLogger().info("BlueskyPluginが無効になりました！");
    }
    
    // データを保存するメソッド
    private void saveData() {
        try {
            File dataFolder = getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            
            File file = new File(dataFolder, "userdata.json");
            JSONObject data = new JSONObject();
            
            // ログイン情報を保存
            JSONObject tokensData = new JSONObject();
            JSONObject handlesData = new JSONObject();
            JSONObject langData = new JSONObject();
            
            userTokens.forEach((uuid, token) -> tokensData.put(uuid.toString(), token));
            userHandles.forEach((uuid, handle) -> handlesData.put(uuid.toString(), handle));
            userLang.forEach((uuid, lang) -> langData.put(uuid.toString(), lang));
            
            data.put("tokens", tokensData);
            data.put("handles", handlesData);
            data.put("lang", langData);
            
            // ファイルに書き込み
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(data.toString(2));
            }
        } catch (Exception e) {
            getLogger().warning("Error saving data: " + e.getMessage());
        }
    }
    
    // データを読み込むメソッド
    private void loadData() {
        try {
            File file = new File(getDataFolder(), "userdata.json");
            if (!file.exists()) {
                return;
            }
            
            // ファイルを読み込み
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line);
                }
            }
            
            // JSONデータをパース
            JSONObject data = new JSONObject(content.toString());
            
            // トークンを読み込み
            JSONObject tokensData = data.getJSONObject("tokens");
            tokensData.keys().forEachRemaining(uuidStr -> {
                UUID uuid = UUID.fromString(uuidStr);
                String token = tokensData.getString(uuidStr);
                userTokens.put(uuid, token);
            });
            
            // ハンドルを読み込み
            JSONObject handlesData = data.getJSONObject("handles");
            handlesData.keys().forEachRemaining(uuidStr -> {
                UUID uuid = UUID.fromString(uuidStr);
                String handle = handlesData.getString(uuidStr);
                userHandles.put(uuid, handle);
            });

            // 言語設定を読み込み (存在しない場合は日本語)
            if (data.has("lang")) {
                JSONObject langData = data.getJSONObject("lang");
                langData.keys().forEachRemaining(uuidStr -> {
                    UUID uuid = UUID.fromString(uuidStr);
                    String lang = langData.getString(uuidStr);
                    userLang.put(uuid, lang);
                });
            }
        } catch (Exception e) {
            getLogger().warning("Error loading data: " + e.getMessage());
        }
    }

    // 初期メッセージを設定
    private void initMessages() {
        Map<String, String> ja = new HashMap<>();
        Map<String, String> en = new HashMap<>();

        ja.put("usage_login", "使用方法: /bsky login <handle> <password>");
        en.put("usage_login", "Usage: /bsky login <handle> <password>");

        ja.put("usage_post", "使用方法: /bsky post <メッセージ>");
        en.put("usage_post", "Usage: /bsky post <message>");

        ja.put("usage_lang", "使用方法: /bsky lang <english|japanese>");
        en.put("usage_lang", "Usage: /bsky lang <english|japanese>");

        ja.put("must_login", "先にログインしてください！");
        en.put("must_login", "Please login first!");

        ja.put("login_success", "ログインに成功しました！");
        en.put("login_success", "Login successful!");

        ja.put("login_failed", "ログインに失敗しました。");
        en.put("login_failed", "Login failed.");

        ja.put("post_success", "投稿に成功しました！");
        en.put("post_success", "Post succeeded!");

        ja.put("not_logged_in", "ログインしていません。");
        en.put("not_logged_in", "You are not logged in.");

        ja.put("logout_success", "ログアウトしました。");
        en.put("logout_success", "Logged out.");

        ja.put("invalid_command", "無効なコマンドです。");
        en.put("invalid_command", "Invalid command.");

        ja.put("timeline_header", "=== Blueskyタイムライン ===");
        en.put("timeline_header", "=== Bluesky Timeline ===");

        ja.put("timeline_failed", "タイムラインの取得に失敗しました。");
        en.put("timeline_failed", "Failed to retrieve timeline.");

        ja.put("feed_list_failed", "フィード一覧の取得に失敗しました。");
        en.put("feed_list_failed", "Failed to retrieve feed list.");

        ja.put("lang_changed_en", "言語を英語に変更しました。/bsky lang japanese で日本語に戻せます。");
        en.put("lang_changed_en", "Language changed to English. Use /bsky lang japanese to switch back.");

        ja.put("lang_changed_ja", "言語を日本語に変更しました。/bsky lang english で英語に切り替えられます。");
        en.put("lang_changed_ja", "Language changed to Japanese. Use /bsky lang english to switch to English.");

        messages.put("japanese", ja);
        messages.put("english", en);
    }

    // 言語に基づいてメッセージを送信
    private void sendLocalized(Player player, String key, Object... args) {
        UUID uuid = player.getUniqueId();
        String lang = userLang.getOrDefault(uuid, "japanese");
        Map<String, String> dict = messages.getOrDefault(lang, messages.get("japanese"));
        String template = dict.getOrDefault(key, messages.get("english").getOrDefault(key, key));
        try {
            String msg = args == null || args.length == 0 ? template : String.format(template, args);
            player.sendMessage(msg);
        } catch (Exception e) {
            player.sendMessage(template);
        }
    }

    // 言語切替処理
    private void handleLang(Player player, String langArg) {
        String normalized = langArg.toLowerCase();
        UUID uuid = player.getUniqueId();
        if (normalized.equals("english") || normalized.equals("en")) {
            userLang.put(uuid, "english");
            saveData();
            sendLocalized(player, "lang_changed_en");
        } else if (normalized.equals("japanese") || normalized.equals("ja") || normalized.equals("jp")) {
            userLang.put(uuid, "japanese");
            saveData();
            sendLocalized(player, "lang_changed_ja");
        } else {
            sendLocalized(player, "usage_lang");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;
        UUID playerId = player.getUniqueId();

        if (args.length == 0) {
            player.sendMessage("使用方法: /bsky <login|logout|post|tl>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "login":
                if (args.length != 3) {
                    sendLocalized(player, "usage_login");
                    return true;
                }
                handleLogin(player, args[1], args[2]);
                break;
            case "logout":
                handleLogout(player);
                break;
            case "post":
                if (!userTokens.containsKey(playerId)) {
                    sendLocalized(player, "must_login");
                    return true;
                }
                if (args.length < 2) {
                    sendLocalized(player, "usage_post");
                    return true;
                }
                String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                handlePost(player, message);
                break;
            case "tl":
                if (!userTokens.containsKey(playerId)) {
                    sendLocalized(player, "must_login");
                    return true;
                }
                handleTimeline(player);
                break;
            case "lang":
                if (args.length != 2) {
                    sendLocalized(player, "usage_lang");
                    return true;
                }
                handleLang(player, args[1]);
                break;
            default:
                sendLocalized(player, "invalid_command");
                break;
        }
        return true;
    }

    private void handleLogin(Player player, String handle, String password) {
        try {
            // .bsky.socialを自動的に追加
            String fullHandle = handle.contains(".") ? handle : handle + ".bsky.social";
            
            JSONObject loginData = new JSONObject();
            loginData.put("identifier", fullHandle);
            loginData.put("password", password);

            URL url = new URL("https://bsky.social/xrpc/com.atproto.server.createSession");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            try (DataOutputStream wr = new DataOutputStream(conn.getOutputStream())) {
                wr.write(loginData.toString().getBytes(StandardCharsets.UTF_8));
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
            }

            JSONObject responseJson = new JSONObject(response.toString());
            int responseCode = conn.getResponseCode();

            if (responseCode == 200) {
                String accessJwt = responseJson.getString("accessJwt");
                userTokens.put(player.getUniqueId(), accessJwt);
                // 常にフルハンドルを保存
                userHandles.put(player.getUniqueId(), fullHandle);
                // デフォルト言語がなければ日本語を設定
                userLang.putIfAbsent(player.getUniqueId(), "japanese");
                sendLocalized(player, "login_success");
            } else {
                sendLocalized(player, "login_failed");
            }
        } catch (Exception e) {
            player.sendMessage("Error occurred: " + e.getMessage());
        }
    }

    private void handlePost(Player player, String text) {
        try {
            String accessJwt = userTokens.get(player.getUniqueId());
            String handle = userHandles.get(player.getUniqueId());

            // handleが完全な形式（.bsky.social付き）であることを確認
            if (!handle.contains(".")) {
                handle = handle + ".bsky.social";
            }
            
            // 最初にDIDを取得
            URL profileUrl = new URL("https://bsky.social/xrpc/app.bsky.actor.getProfile?actor=" + handle);
            HttpURLConnection profileConn = (HttpURLConnection) profileUrl.openConnection();
            profileConn.setRequestMethod("GET");
            profileConn.setRequestProperty("Authorization", "Bearer " + accessJwt);

            StringBuilder profileResponse = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(profileConn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    profileResponse.append(line);
                }
            }

            JSONObject profileJson = new JSONObject(profileResponse.toString());
            String did = profileJson.getString("did");

            // 投稿データを作成
            JSONObject postData = new JSONObject();
            postData.put("repo", did);
            postData.put("collection", "app.bsky.feed.post");
            
            JSONObject recordData = new JSONObject();
            recordData.put("text", text);
            recordData.put("createdAt", java.time.Instant.now().toString());
            postData.put("record", recordData);

            URL url = new URL("https://bsky.social/xrpc/com.atproto.repo.createRecord");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + accessJwt);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            try (DataOutputStream wr = new DataOutputStream(conn.getOutputStream())) {
                wr.write(postData.toString().getBytes(StandardCharsets.UTF_8));
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    conn.getResponseCode() == 200 ? conn.getInputStream() : conn.getErrorStream(), 
                    StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                sendLocalized(player, "post_success");
            } else {
                player.sendMessage("Post failed. Error: " + response.toString());
            }
        } catch (Exception e) {
            player.sendMessage("Error occurred: " + e.getMessage());
            getLogger().warning("投稿中にエラーが発生しました: " + e.getMessage());
        }
    }

    private void handleLogout(Player player) {
        UUID playerId = player.getUniqueId();
        if (!userTokens.containsKey(playerId)) {
            sendLocalized(player, "not_logged_in");
            return;
        }
        
        userTokens.remove(playerId);
        userHandles.remove(playerId);
        // 変更をファイルに保存
        saveData();
        sendLocalized(player, "logout_success");
    }

    private void handleTimeline(Player player) {
        try {
            String accessJwt = userTokens.get(player.getUniqueId());

            URL url = new URL("https://bsky.social/xrpc/app.bsky.feed.getTimeline");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + accessJwt);

            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
            }

            JSONObject responseJson = new JSONObject(response.toString());
            int responseCode = conn.getResponseCode();

            if (responseCode == 200) {
                sendLocalized(player, "timeline_header");
                responseJson.getJSONArray("feed").forEach(post -> {
                    JSONObject postObj = (JSONObject) post;
                    JSONObject postView = postObj.getJSONObject("post");
                    JSONObject author = postView.getJSONObject("author");
                    String displayName = author.getString("displayName");
                    String handle = author.getString("handle");
                    String text = postView.getJSONObject("record").getString("text");
                    player.sendMessage("§6" + displayName + " §b(@" + handle + ")§r: " + text);
                });
            } else {
                sendLocalized(player, "timeline_failed");
            }
        } catch (Exception e) {
            player.sendMessage("Error occurred: " + e.getMessage());
        }
    }

    private void handleFeedList(Player player) {
        try {
            String accessJwt = userTokens.get(player.getUniqueId());

            // 保存済みフィードを取得
            URL savedFeedsUrl = new URL("https://bsky.social/xrpc/app.bsky.feed.getFeedGenerators");
            HttpURLConnection savedFeedsConn = (HttpURLConnection) savedFeedsUrl.openConnection();
            savedFeedsConn.setRequestMethod("GET");
            savedFeedsConn.setRequestProperty("Authorization", "Bearer " + accessJwt);

            StringBuilder savedFeedsResponse = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(savedFeedsConn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    savedFeedsResponse.append(line);
                }
            }

            // 保存済みフィードの表示
            JSONObject savedFeedsJson = new JSONObject(savedFeedsResponse.toString());
            player.sendMessage("=== 保存済みカスタムフィード ===");
            List<String> feedNames = new ArrayList<>();
            savedFeedsJson.getJSONArray("feeds").forEach(feed -> {
                JSONObject feedObj = (JSONObject) feed;
                JSONObject generator = feedObj.getJSONObject("generator");
                String feedName = generator.getString("displayName");
                String uri = generator.getString("uri");
                feedNames.add(feedName);
                player.sendMessage("- " + feedName + " (URI: " + uri + ")");
            });
            
            // フィード名をキャッシュ
            userFeeds.put(player.getUniqueId(), feedNames);

            // ユーザーの作成したフィードを取得
            String handle = userHandles.get(player.getUniqueId());
            URL url = new URL("https://bsky.social/xrpc/app.bsky.feed.getActorFeeds?actor=" + handle + "&limit=100");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + accessJwt);

            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
            }

            JSONObject responseJson = new JSONObject(response.toString());
            int responseCode = conn.getResponseCode();

            if (responseCode == 200) {
                player.sendMessage("\n=== Created custom feeds ===");
                responseJson.getJSONArray("feeds").forEach(feed -> {
                    JSONObject feedObj = (JSONObject) feed;
                    String feedName = feedObj.getString("displayName");
                    String uri = feedObj.getString("uri");
                    player.sendMessage("- " + feedName + " (URI: " + uri + ")");
                });
            } else {
                sendLocalized(player, "feed_list_failed");
            }
        } catch (Exception e) {
            player.sendMessage("Error occurred: " + e.getMessage());
        }
    }

    private void handleFeedTimeline(Player player, String feedUri) {
        try {
            String accessJwt = userTokens.get(player.getUniqueId());

            // 全保存済みフィードから該当するフィードを探す
            URL savedFeedsUrl = new URL("https://bsky.social/xrpc/app.bsky.feed.getFeedGenerators");
            HttpURLConnection savedFeedsConn = (HttpURLConnection) savedFeedsUrl.openConnection();
            savedFeedsConn.setRequestMethod("GET");
            savedFeedsConn.setRequestProperty("Authorization", "Bearer " + accessJwt);

            String targetUri = null;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(savedFeedsConn.getInputStream(), StandardCharsets.UTF_8))) {
                JSONObject savedFeeds = new JSONObject(br.lines().collect(java.util.stream.Collectors.joining()));
                JSONArray feeds = savedFeeds.getJSONArray("feeds");
                for (int i = 0; i < feeds.length(); i++) {
                    JSONObject feed = feeds.getJSONObject(i);
                    JSONObject generator = feed.getJSONObject("generator");
                    String uri = generator.getString("uri");
                    if (uri.contains(feedUri) || generator.getString("displayName").equalsIgnoreCase(feedUri)) {
                        targetUri = uri;
                        break;
                    }
                }
            }

            // 保存済みフィードで見つからない場合は、ユーザーの作成したフィードから探す
            if (targetUri == null) {
                String handle = userHandles.get(player.getUniqueId());
                URL listUrl = new URL("https://bsky.social/xrpc/app.bsky.feed.getActorFeeds?actor=" + handle);
                HttpURLConnection listConn = (HttpURLConnection) listUrl.openConnection();
                listConn.setRequestMethod("GET");
                listConn.setRequestProperty("Authorization", "Bearer " + accessJwt);

                try (BufferedReader br = new BufferedReader(new InputStreamReader(listConn.getInputStream(), StandardCharsets.UTF_8))) {
                    JSONObject feedList = new JSONObject(br.lines().collect(java.util.stream.Collectors.joining()));
                    JSONArray feeds = feedList.getJSONArray("feeds");
                    for (int i = 0; i < feeds.length(); i++) {
                        JSONObject feed = feeds.getJSONObject(i);
                        if (feed.getString("displayName").equalsIgnoreCase(feedUri)) {
                            targetUri = feed.getString("uri");
                            break;
                        }
                    }
                }

                if (targetUri != null) {
                    feedUri = targetUri;
                } else {
                    // フィード名が見つからない場合は、ハンドルとしてDIDに変換を試みる
                    try {
                        URL profileUrl = new URL("https://bsky.social/xrpc/app.bsky.actor.getProfile?actor=" + feedUri.split("/")[0]);
                        HttpURLConnection profileConn = (HttpURLConnection) profileUrl.openConnection();
                        profileConn.setRequestMethod("GET");
                        profileConn.setRequestProperty("Authorization", "Bearer " + accessJwt);

                        try (BufferedReader br = new BufferedReader(new InputStreamReader(profileConn.getInputStream(), StandardCharsets.UTF_8))) {
                            JSONObject profile = new JSONObject(br.lines().collect(java.util.stream.Collectors.joining()));
                            String did = profile.getString("did");
                            feedUri = "at://" + did + "/" + String.join("/", java.util.Arrays.copyOfRange(feedUri.split("/"), 1, feedUri.split("/").length));
                        }
                    } catch (Exception e) {
                        player.sendMessage("フィードの検索中にエラーが発生しました: " + e.getMessage());
                        return;
                    }
                }
            }

            URL url = new URL("https://bsky.social/xrpc/app.bsky.feed.getFeed?feed=" + java.net.URLEncoder.encode(feedUri, "UTF-8"));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + accessJwt);

            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
            }

            JSONObject responseJson = new JSONObject(response.toString());
            int responseCode = conn.getResponseCode();

            if (responseCode == 200) {
                player.sendMessage("=== カスタムフィード: " + feedUri + " ===");
                responseJson.getJSONArray("feed").forEach(post -> {
                    try {
                        JSONObject postObj = (JSONObject) post;
                        JSONObject postView = postObj.getJSONObject("post");
                        JSONObject author = postView.getJSONObject("author");
                        String displayName = author.getString("displayName");
                        String handle = author.getString("handle");
                        String text = postView.getJSONObject("record").getString("text");
                        player.sendMessage("§6" + displayName + " (@" + handle + ")§r: " + text);
                    } catch (Exception e) {
                        player.sendMessage("投稿の解析中にエラーが発生しました: " + e.getMessage());
                    }
                });
            } else {
                player.sendMessage("フィードの取得に失敗しました。");
            }
        } catch (Exception e) {
            player.sendMessage("エラーが発生しました: " + e.getMessage());
        }
    }
}