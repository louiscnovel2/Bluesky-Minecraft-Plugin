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

public class BlueskyPlugin extends JavaPlugin {
    private Map<UUID, String> userTokens = new HashMap<>();
    private Map<UUID, String> userHandles = new HashMap<>();
    private Map<UUID, List<String>> userFeeds = new HashMap<>();

    @Override
    public void onEnable() {
        getLogger().info("BlueskyPluginが有効になりました！");
        
        // 保存されたデータを読み込む
        loadData();
        
        // コマンドのTab補完を登録
        getCommand("bsky").setTabCompleter((sender, command, alias, args) -> {
            List<String> completions = new ArrayList<>();
            if (!(sender instanceof Player)) {
                return completions;
            }
            
            if (args.length == 1) {
                // 最初の引数の候補
                String[] commands = {"login", "logout", "post", "tl"};
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
            
            userTokens.forEach((uuid, token) -> tokensData.put(uuid.toString(), token));
            userHandles.forEach((uuid, handle) -> handlesData.put(uuid.toString(), handle));
            
            data.put("tokens", tokensData);
            data.put("handles", handlesData);
            
            // ファイルに書き込み
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(data.toString(2));
            }
        } catch (Exception e) {
            getLogger().warning("データの保存中にエラーが発生しました: " + e.getMessage());
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
        } catch (Exception e) {
            getLogger().warning("データの読み込み中にエラーが発生しました: " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("このコマンドはプレイヤーのみが実行できます。");
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
                    player.sendMessage("使用方法: /bsky login <handle> <password>");
                    return true;
                }
                handleLogin(player, args[1], args[2]);
                break;
            case "logout":
                handleLogout(player);
                break;
            case "post":
                if (!userTokens.containsKey(playerId)) {
                    player.sendMessage("先にログインしてください！");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("使用方法: /bsky post <メッセージ>");
                    return true;
                }
                String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                handlePost(player, message);
                break;
            case "tl":
                if (!userTokens.containsKey(playerId)) {
                    player.sendMessage("先にログインしてください！");
                    return true;
                }
                handleTimeline(player);
                break;
            default:
                player.sendMessage("無効なコマンドです。");
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
                player.sendMessage("ログインに成功しました！");
            } else {
                player.sendMessage("ログインに失敗しました。");
            }
        } catch (Exception e) {
            player.sendMessage("エラーが発生しました: " + e.getMessage());
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
                player.sendMessage("投稿に成功しました！");
            } else {
                player.sendMessage("投稿に失敗しました。エラー: " + response.toString());
            }
        } catch (Exception e) {
            player.sendMessage("エラーが発生しました: " + e.getMessage());
            getLogger().warning("投稿中にエラーが発生しました: " + e.getMessage());
        }
    }

    private void handleLogout(Player player) {
        UUID playerId = player.getUniqueId();
        if (!userTokens.containsKey(playerId)) {
            player.sendMessage("ログインしていません。");
            return;
        }
        
        userTokens.remove(playerId);
        userHandles.remove(playerId);
        // 変更をファイルに保存
        saveData();
        player.sendMessage("ログアウトしました。");
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
                player.sendMessage("=== Blueskyタイムライン ===");
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
                player.sendMessage("タイムラインの取得に失敗しました。");
            }
        } catch (Exception e) {
            player.sendMessage("エラーが発生しました: " + e.getMessage());
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
                player.sendMessage("\n=== 作成したカスタムフィード ===");
                responseJson.getJSONArray("feeds").forEach(feed -> {
                    JSONObject feedObj = (JSONObject) feed;
                    String feedName = feedObj.getString("displayName");
                    String uri = feedObj.getString("uri");
                    player.sendMessage("- " + feedName + " (URI: " + uri + ")");
                });
            } else {
                player.sendMessage("フィード一覧の取得に失敗しました。");
            }
        } catch (Exception e) {
            player.sendMessage("エラーが発生しました: " + e.getMessage());
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