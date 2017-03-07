//package com.carbondev.andromeda;

package com.gm.android.volley;
/*from  w w  w .  j  a va  2  s. com*/
import android.util.Base64;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.HurlStack;


import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;

import java.io.IOException;
import java.math.BigInteger;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLSocketFactory;
import com.gm.android.volley.Headers;

/**
 * Created by gsmiro on 15/05/2014.
 */
public class HttpDigestStack extends HurlStack {

    private Map<String, Map<String, String>> authCache = new HashMap<String, Map<String, String>>();
    private MessageDigest md;
    private Proxy proxy;
    private DigestAuthenticator authenticator;

    public HttpDigestStack() {
        super();
        this.authenticator = new DigestAuthenticator() {
            @Override
            protected PasswordAuthentication requestPasswordAuthentication(String rHost, InetAddress rAddr, int rPort, String rProtocol, String realm, String scheme, URL rURL, Authenticator.RequestorType reqType) {
                return null;
            }
        };
    }

    public HttpDigestStack(UrlRewriter urlRewriter) {
        super(urlRewriter);
    }

    public HttpDigestStack(UrlRewriter urlRewriter, SSLSocketFactory sslSocketFactory) {
        super(urlRewriter, sslSocketFactory);
    }

    public HttpDigestStack(UrlRewriter urlRewriter, SSLSocketFactory sslSocketFactory, Proxy proxy) {
        super(urlRewriter, sslSocketFactory);
        this.proxy = proxy;
    }

    public HttpDigestStack(DigestAuthenticator auth, UrlRewriter urlRewriter) {
        super(urlRewriter);
        this.authenticator = auth;
    }

    public HttpDigestStack(DigestAuthenticator auth, UrlRewriter urlRewriter, SSLSocketFactory sslSocketFactory) {
        super(urlRewriter, sslSocketFactory);
        this.authenticator = auth;
    }

    public HttpDigestStack(DigestAuthenticator auth, UrlRewriter urlRewriter, SSLSocketFactory sslSocketFactory, Proxy proxy) {
        super(urlRewriter, sslSocketFactory);
        this.proxy = proxy;
        this.authenticator = auth;
    }


    @Override
    protected HttpURLConnection createConnection(URL url) throws IOException {
        if (this.proxy == null)
            return super.createConnection(url);
        else return (HttpURLConnection) url.openConnection(proxy);
    }

    @Override
    public HttpResponse performRequest
            (Request<?> request, Map<String, String> additionalHeaders) throws
            IOException, AuthFailureError {
        if (!request.getHeaders().containsKey(Headers.Authorization.val()) && !additionalHeaders.containsKey(Headers.Authorization.val())) {
            authenticator.url = request.getUrl();
            String auth = authenticator.getAuthToken();
            if (auth != null)
                additionalHeaders.put(Headers.Authorization.val(), auth);
            else {
                additionalHeaders.remove(Headers.Authorization.val());
                request.getHeaders().remove(Headers.Authorization.val());
            }
        }
        HttpResponse response = super.performRequest(request, additionalHeaders);
        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
            request.addMarker("auth-required");
            if (authenticator.setAuthToken(request, response, authCache)) {
                request.addMarker("auth-set");
                return super.performRequest(request, additionalHeaders);
            }
        }
        return response;
    }


    public static abstract class DigestAuthenticator implements com.android.volley.toolbox.Authenticator {

        private Map<String, Map<String, String>> authCache = new HashMap<>();
        protected String url;
        private MessageDigest md;

        @Override
        public final String getAuthToken() throws AuthFailureError {
            Map<String, String> authValues = authCache.get(url);
            if (authValues != null) {
                String qop = authValues.get("qop");
                if ("auth".equals(qop) || "auth-int".equals(qop)) {
                    String h1 = authValues.get("h1");
                    String h2 = authValues.get("h2");

                    MessageDigest md5 = null;
                    try {
                        md5 = getDigest(authValues.get("algorithm"));
                    } catch (NoSuchAlgorithmException e) {
                        throw new AuthFailureError("Unknown algorithm", e);
                    }
                    authValues.put("nc", incNonceCount(authValues.get("nc")));
                    authValues.put("response", digest(md5, h1, authValues.get("nonce"), authValues.get("nc"),
                            authValues.get("cnonce"), qop, h2));
                }
                return Headers.Authorization.make(authValues);
            }
            return null;
        }

        @Override
        public final void invalidateAuthToken(String authToken) {
            authCache.remove(authToken);
        }


        protected final MessageDigest getDigest(String algorithm) throws NoSuchAlgorithmException {
            if (md != null && md.getAlgorithm().equals(algorithm)) return this.md;
            if (algorithm == null)
                this.md = MessageDigest.getInstance("MD5");
            else
                this.md = MessageDigest.getInstance(algorithm);
            return this.md;
        }

        protected String incNonceCount(String nc) {
            if (nc == null || "".equals(nc)) return BigInteger.ZERO.toString(16);
            else
                try {
                    BigInteger onc = new BigInteger(nc);
                    return onc.add(BigInteger.ONE).toString(16);
                } catch (NumberFormatException e) {

                }
            return BigInteger.ZERO.toString(16);
        }

        protected final String getCNonce(Map<String, String> header, Header hetag, String pwd) {
            String etag = hetag == null ? null : hetag.getValue();
            if (header.containsKey("cnonce")) return header.get("cnonce");

            String cnonce = "";
            if (etag != null && etag.length() > 0)
                cnonce += etag + ":";
            cnonce += Calendar.getInstance().getTimeInMillis() + ":";
            cnonce += pwd;
            cnonce = Base64.encodeToString(cnonce.getBytes(), Base64.URL_SAFE | Base64.NO_WRAP);
            return cnonce;
        }

        //if server supports auth and auth-int, use auth-int, if auth only, return auth and an empty string otherwise.
        private String getQopValue(String value) {
            if (value == null) return "";
            for (String s : value.split(",")) {
                if ("auth-int".equals(s.trim())) return s;
                if ("auth".equals(s.trim())) value = s;
            }
            return value;
        }

        private String digest(MessageDigest digest, String method, byte[] content) {
            digest.reset();
            digest.update(method.getBytes());
            digest.update(":".getBytes());
            if (content != null)
                digest.update(content);
            return new BigInteger(1, digest.digest()).toString(16);
        }

        private String digest(MessageDigest digest, String... s) {
            digest.reset();
            boolean nfirst = false;
            String res = "";
            for (String str : s) {
                if (str != null) {
                    if (nfirst)
                        res += ":";
                    else
                        nfirst = true;

                    res += str;
                }
            }
            VolleyLog.d(res);
            res = new BigInteger(1, digest.digest(res.getBytes())).toString(16);
            VolleyLog.d(res);
            return res;

        }


        protected final Boolean setAuthToken(Request request, HttpResponse httpResponse, Map<String, Map<String, String>> authCache) throws AuthFailureError {
            try {
                if (!httpResponse.containsHeader(Headers.WWWAuthenticate.val())) {
                    VolleyLog.wtf("No WWW-Authenticate header found. Ignoring.");
                    return false;
                }
                URL url = new URL(request.getUrl());
                Header www = httpResponse.getFirstHeader(Headers.WWWAuthenticate.val());
                VolleyLog.d("WWW-Authenticate header:" + www.getValue());
                Map<String, String> header = Headers.WWWAuthenticate.toMap(www);
                String realm = header.get("realm");
                PasswordAuthentication auth = requestPasswordAuthentication(url.getHost(), InetAddress.getByName(url.getHost()), url.getPort(), url.getProtocol(), realm, "Digest", url, Authenticator.RequestorType.SERVER);
                if (auth != null) {
                    String algorithm = header.get("algorithm");
                    if (algorithm == null || "".equals(algorithm) || "MD5(-sess)*".matches(algorithm))
                        algorithm = "MD5";

                    MessageDigest md5 = getDigest(algorithm);

                    String pwdMd5 = digest(md5, new String(auth.getPassword()));
                    String h1 = digest(md5, auth.getUserName(), realm, pwdMd5);
                    VolleyLog.d("h1:%s, user:%s, realm:%s, password:%s", h1, auth, realm, pwdMd5);
                    if ("MD5-sess".equalsIgnoreCase(algorithm)) {//should be done only once by the spec
                        header.put("cnonce", getCNonce(header, httpResponse.getFirstHeader("Etag"), pwdMd5));
                        h1 = digest(md5, h1, header.get("nonce"), header.get("cnonce"));
                        VolleyLog.d("h1:%s, nonce:%s, cnonce:%s", h1, header.get("nonce"), header.get("cnonce"));
                    }
                    String uri = header.get("uri");
                    if (uri == null || "".equals(uri)) {
                        uri = request.getUrl();
                        header.put("uri", uri);
                    }

                    String qop = getQopValue(header.get("qop"));
                    String h2;
                    if ("auth-int".equals(qop)) {
                        String method = requestMethod(request);
                        h2 = digest(md5, method, request.getBody());
                        VolleyLog.d("h2:%s, method:%s, body", h2, method);
                    } else {
                        String method = requestMethod(request);
                        h2 = digest(md5, method, uri);
                        VolleyLog.d("h2:%s, method:%s, uri:%s", h2, method, uri);
                    }
                    if ("auth".equals(qop) || "auth-int".equals(qop)) {
                        header.put("qop", qop);
                        header.put("cnonce", getCNonce(header, httpResponse.getFirstHeader("Etag"), pwdMd5));
                        header.put("h1", h1);
                        header.put("h2", h2);
                    } else {
                        header.put("response", digest(md5, h1, header.get("nonce"), h2));
                    }
                    String domain = header.remove("domain");
                    if (domain != null && domain.length() > 0) {
                        for (String uris : domain.split(" ")) {
                            authCache.put(uris, header);
                        }
                    }

                    header.put("username", auth.getUserName());
                    authCache.put(request.getUrl(), header);
                    VolleyLog.d("%s", authCache);
                    return true;
                } else return false;
            } catch (NoSuchAlgorithmException | MalformedURLException | UnknownHostException e) {
                throw new AuthFailureError("Cannot calculate header value", e);
            }
        }

        private String requestMethod(Request request) {
            switch (request.getMethod()) {
                case Request.Method.GET:
                    return "GET";
                case Request.Method.POST:
                    return "POST";
                case Request.Method.PUT:
                    return "PUT";
                case Request.Method.DELETE:
                    return "DELETE";
                case Request.Method.HEAD:
                    return "HEAD";
                case Request.Method.OPTIONS:
                    return "OPTIONS";
                case Request.Method.TRACE:
                    return "TRACE";
                case Request.Method.PATCH:
                    return "PATCH";
                default:
                    return "GET";
            }

        }

        protected abstract PasswordAuthentication requestPasswordAuthentication(String rHost, InetAddress rAddr, int rPort, String rProtocol, String realm, String scheme, URL rURL, Authenticator.RequestorType reqType);

    }

}