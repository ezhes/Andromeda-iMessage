package com.gm.android.volley;
//from  w  ww .j a v  a 2s  . c  om
import com.android.volley.VolleyLog;

import org.apache.http.Header;
import org.apache.http.HeaderElement;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public enum Headers {
    //According to RFC2617
    WWWAuthenticate("WWW-Authenticate", new String[]{"realm", "nonce", "opaque", "stale", "algorithm", "qop", "domain"}),
    Authorization(new String[]{"realm", "nonce", "opaque", "stale", "algorithm", "qop", "username", "uri", "cnonce", "nc", "response"}),
    AuthenticationInfo("Authentication-Info", new String[]{"qop", "nc", "digest", "nextnonce", "rspauth"});

    private String val;
    private String[] vals;
    private Pattern regex;

    private Headers(String[] headerValues) {
        init(this.name(), headerValues);
    }

    private Headers(String val, String[] headerValues) {
        init(val, headerValues);
    }

    private void init(String val, String[] headerValues) {
        this.val = val;
        if (headerValues == null) {
            this.vals = new String[0];
            this.regex = Pattern.compile("");
        } else {
            this.vals = headerValues == null ? new String[0] : headerValues;
            this.regex = initPattern(this.vals);
        }
    }

    private Pattern initPattern(String[] vals) {
        String concat = "";
        for (String v : vals) {
            if (concat.isEmpty())
                concat += val;
            else concat += "|" + val;
        }
        return Pattern.compile("(" + concat + ")=(\"?(\\w+)\"?)");
    }

    public String val() {
        return this.val;
    }

    public String[] headerValues() {
        return this.vals;
    }

    public Map<String, String> toMap(Header headerValue) {
        LinkedHashMap<String, String> header = new LinkedHashMap<String,String>(this.vals.length);
        for (HeaderElement he : headerValue.getElements()) {
            for (String val : this.vals) {
                if (he.getName().contains(val))
                    header.put(val, he.getValue());
            }
        }
        VolleyLog.d("Authorization values: %s", header);
        return header;
    }

    public String make(Map<String, String> values) {
        String value = "Digest ";
        boolean comma = false;
        for (String val : this.vals) {
            String hv = values.get(val);
            if (hv != null) {
                if (comma) value += ", ";
                else comma = true;
                value += val + "=";
                if ("nc".equals(val) || "qop".equals(val)) {
                    value += hv;
                } else {
                    value += "\"" + hv + "\"";
                }
            }
        }
        VolleyLog.d("%s header: %s", this.val, value);
        return value;
    }
}