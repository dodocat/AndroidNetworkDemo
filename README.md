# Android Network Demo

This demo shows how to use volley with OkHttp and security your api with https.
本文同步发于[简书](http://www.jianshu.com/p/e58161cbc3a4)

## 代码结构说明
* `tools` 文件夹下是相关的工具 `dumps.sh` 是一个导出证书并转换成 BKS 存储的完整脚本.
* `MainActivity` 简单的调用了了一下相关实现进行验证.
* `OkHttpStack` OkHttp 实现的 HttpStack
* `SelfSignSslOkHttpStack` 检验自签名证书的 HttpStack 实现.

##  使用 OkHttp 作为传输层的实现.
Volley 默认根据 Android 系统版本使用不同的 Http 传输协议实现. 3.0 以上使用HttpUrlConnection, 2.3 以下使用 ApacheHttpStack, 参考[Android Http Client].

OkHttp 相较于其它的实现有以下的优点.
* 支持[SPDY](http://zh.wikipedia.org/wiki/SPDY)，允许连接同一主机的所有请求分享一个socket。
* 如果SPDY不可用，会使用连接池减少请求延迟。
* 使用GZIP压缩下载内容，且压缩操作对用户是透明的。
* 利用响应缓存来避免重复的网络请求。
* 当网络出现问题的时候，OKHttp会依然有效，它将从常见的连接问题当中恢复。
* 如果你的服务端有多个IP地址，当第一个地址连接失败时，OKHttp会尝试连接其他的地址，这对IPV4和IPV6以及寄宿在多个数据中心的服务而言，是非常有必要的。

因此使用 OkHttp 作为替代是好的选择.

首先用 OkHttp 实现一个新的 `HurlStack` 用于构建 Volley 的 requestQueue.

``` java
public class OkHttpStack extends HurlStack {

    private OkHttpClient okHttpClient;

    /**
     * Create a OkHttpStack with default OkHttpClient.
     */
    public OkHttpStack() {
        this(new OkHttpClient());
    }

    /**
     * Create a OkHttpStack with a custom OkHttpClient
     * @param okHttpClient Custom OkHttpClient, NonNull
     */
    public OkHttpStack(OkHttpClient okHttpClient) {
        this.okHttpClient = okHttpClient;
    }

    @Override
    protected HttpURLConnection createConnection(URL url) throws IOException {
        OkUrlFactory okUrlFactory = new OkUrlFactory(okHttpClient);
        return okUrlFactory.open(url);
    }
}
```

然后使用 OkHttpStack 创建新的 Volley requestQueue.
``` java
requestQueue = Volley.newRequestQueue(getContext(), new OkHttpStack());
requestQueue.start();
```
这样就行了.

# 使用 Https
作为一个有节操的开发者应该使用 Https 来保护用户的数据, Android 开发者网站上文章[Security with HTTPS and SSL]做了详尽的阐述.

OkHttp 自身是支持 Https 的. 参考文档 [OkHttp Https], 直接使用上面的 `OkHttpStack` 就可以了, 但是如果遇到服务器开发哥哥使用了自签名的证书(不要问我为什么要用自签名的), 就无法正常访问了.

 网上有很多文章给出的方案是提供一个什么事情都不做的`TrustManager` 跳过 `SSL` 的验证, 这样做很容受到攻击, Https 也就形同虚设了.

我采用的方案是将自签名的证书打包入 APK 加入信任.

好处:
* 应用难以逆向, 应用不再依赖系统的 trust store, 使得 Charles 抓包等工具失效. 要分析应用 API 必须反编译 APK.
* 不用额外购买证书, 省钱....
缺点:
* 证书部署灵活性降低, 一旦变更证书必须升级程序.

## 实现步骤
以最著名的自签名网站12306为例说明

1. 导出证书
   ```
    echo | openssl s_client -connect kyfw.12306.cn:443 2>&1 |  sed -ne '/-BEGIN CERTIFICATE-/,/-END CERTIFICATE-/p' > kyfw.12306.cn.pem
   ```

1. 将证书转为 bks 格式
    下载最新的bcprov-jdk, 执行下面的命令. storepass 是导出密钥文件的密码.
    ```
    keytool -importcert -v \
        -trustcacerts \
        -alias 0 \
        -file <(openssl x509 -in kyfw.12306.cn.pem) \
        -keystore kyfw.bks -storetype BKS \
        -providerclass     org.bouncycastle.jce.provider.BouncyCastleProvider \
        -providerpath ./bcprov-jdk16-1.46.jar \
        -storepass asdfqaz
    ```

1. 将导出的 kyfw.bks 文件放入 res/raw 文件夹下.

1. 创建 `SelfSignSslOkHttpStack`
    ```
/**
 * A HttpStack implement witch can verify specified self-signed certification.
 */
public class SelfSignSslOkHttpStack extends HurlStack {

    private OkHttpClient okHttpClient;

    private Map<String, SSLSocketFactory> socketFactoryMap;

    /**
     * Create a OkHttpStack with default OkHttpClient.
     */
    public SelfSignSslOkHttpStack(Map<String, SSLSocketFactory> factoryMap) {
        this(new OkHttpClient(), factoryMap);
    }

    /**
     * Create a OkHttpStack with a custom OkHttpClient
     * @param okHttpClient Custom OkHttpClient, NonNull
     */
    public SelfSignSslOkHttpStack(OkHttpClient okHttpClient, Map<String, SSLSocketFactory> factoryMap) {
        this.okHttpClient = okHttpClient;
        this.socketFactoryMap = factoryMap;
    }

    @Override
    protected HttpURLConnection createConnection(URL url) throws IOException {
        if ("https".equals(url.getProtocol()) && socketFactoryMap.containsKey(url.getHost())) {
            HttpsURLConnection connection = (HttpsURLConnection) new OkUrlFactory(okHttpClient).open(url);
            connection.setSSLSocketFactory(socketFactoryMap.get(url.getHost()));
            return connection;
        } else {
            return  new OkUrlFactory(okHttpClient).open(url);
        }
    }
}
    ```

1. 然后用 `SelfSignSslOkHttpStack` 创建 Volley 的 RequestQueue.

    ```
    String[] hosts = {"kyfw.12306.cn"};
    int[] certRes = {R.raw.kyfw};
    String[] certPass = {"asdfqaz"};
    socketFactoryMap = new Hashtable<>(hosts.length);

    for (int i = 0; i < certRes.length; i++) {
        int res = certRes[i];
        String password = certPass[i];
        SSLSocketFactory sslSocketFactory = createSSLSocketFactory(context, res, password);
        socketFactoryMap.put(hosts[i], sslSocketFactory);
    }

    HurlStack stack = new SelfSignSslOkHttpStack(socketFactoryMap);

    requestQueue = Volley.newRequestQueue(context, stack);
    requestQueue.start();
    ```

1. done

[Volley]:http://developer.android.com/training/volley/index.html
[OkHttp]:http://square.github.io/okhttp/
[Gson]:https://github.com/google/gson

[Security with HTTPS and SSL]:https://developer.android.com/training/articles/security-ssl.html
[OkHttp Https]:https://github.com/square/okhttp/wiki/HTTPS
[Github dodocat/AndroidNetworkDemo]:https://github.com/dodocat/AndroidNetworkdemo
[Android Http Client]:http://android-developers.blogspot.com/2011/09/androids-http-clients.html
