package com.dhruv.glaautologin;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.CaptivePortal;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

public class MainActivity extends Activity {

    public static final String ACTION_CAPTIVE_PORTAL_SIGN_IN = "android.net.conn.CAPTIVE_PORTAL_SIGN_IN";
    private static final String DEFAULT_GLA_PORTAL_URL = "https://captive.onlinegla.com";
    private static final String TRUSTED_HOST = "captive.onlinegla.com";

    private CredentialManager credentialManager;
    private Network captiveNetwork;
    private CaptivePortal captivePortalObj;
    private boolean authenticationDismissed = false;

    private LinearLayout layoutSetup;
    private LinearLayout layoutPortal;
    private EditText editUsername;
    private EditText editPassword;
    private TextView textStatus;
    private TextView portalStatusBanner;
    private ProgressBar portalProgress;
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        credentialManager = new CredentialManager(this);

        layoutSetup = findViewById(R.id.layout_setup);
        layoutPortal = findViewById(R.id.layout_portal);
        editUsername = findViewById(R.id.edit_username);
        editPassword = findViewById(R.id.edit_password);
        textStatus = findViewById(R.id.text_status);
        portalStatusBanner = findViewById(R.id.portal_status_banner);
        portalProgress = findViewById(R.id.portal_progress);
        webView = findViewById(R.id.webview_portal);

        setupUI();
        processIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        processIntent(intent);
    }

    private void setupUI() {
        updateStatusText();

        Button btnSave = findViewById(R.id.btn_save);
        Button btnClear = findViewById(R.id.btn_clear);

        btnSave.setOnClickListener(v -> {
            String u = editUsername.getText().toString();
            String p = editPassword.getText().toString();
            if (u.trim().isEmpty() || p.trim().isEmpty()) {
                Toast.makeText(this, "Enter both username and password", Toast.LENGTH_SHORT).show();
                return;
            }
            credentialManager.saveCredentials(u, p);
            editPassword.setText("");
            updateStatusText();
            Toast.makeText(this, "Credentials securely stored", Toast.LENGTH_SHORT).show();

            if (captiveNetwork != null) {
                launchCaptiveFlow(resolveInitialUrl(getIntent()));
            }
        });

        btnClear.setOnClickListener(v -> {
            credentialManager.clear();
            editUsername.setText("");
            editPassword.setText("");
            updateStatusText();
            Toast.makeText(this, "Credentials cleared", Toast.LENGTH_SHORT).show();
        });
    }

    private void updateStatusText() {
        if (credentialManager.hasCredentials()) {
            textStatus.setText("Status: Configured for user " + credentialManager.getUsername());
        } else {
            textStatus.setText("Status: No credentials configured");
        }
    }

    private void processIntent(Intent intent) {
        if (intent == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            captivePortalObj = intent.getParcelableExtra(ConnectivityManager.EXTRA_CAPTIVE_PORTAL);
        }
        captiveNetwork = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK);

        String action = intent.getAction();
        boolean isCaptiveIntent = ACTION_CAPTIVE_PORTAL_SIGN_IN.equals(action) || captivePortalObj != null;

        if (isCaptiveIntent) {
            bindAppToCaptiveNetwork(captiveNetwork);

            if (credentialManager.hasCredentials()) {
                launchCaptiveFlow(resolveInitialUrl(intent));
            } else {
                layoutPortal.setVisibility(View.GONE);
                layoutSetup.setVisibility(View.VISIBLE);
                Toast.makeText(this, "Please configure your credentials first", Toast.LENGTH_LONG).show();
            }
        } else {
            layoutPortal.setVisibility(View.GONE);
            layoutSetup.setVisibility(View.VISIBLE);
        }
    }

    private String resolveInitialUrl(Intent intent) {
        Uri data = intent.getData();
        if (data != null && ("http".equalsIgnoreCase(data.getScheme()) || "https".equalsIgnoreCase(data.getScheme()))) {
            return data.toString();
        }
        return DEFAULT_GLA_PORTAL_URL;
    }

    private void bindAppToCaptiveNetwork(Network network) {
        if (network == null) return;
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            cm.bindProcessToNetwork(network);
        }
    }

    private void launchCaptiveFlow(String url) {
        layoutSetup.setVisibility(View.GONE);
        layoutPortal.setVisibility(View.VISIBLE);
        portalStatusBanner.setVisibility(View.VISIBLE);
        portalStatusBanner.setText("Connecting to GLA Portal...");
        portalProgress.setVisibility(View.VISIBLE);

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setSaveFormData(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }

        webView.setWebViewClient(new PortalWebViewClient());
        webView.loadUrl(url);
    }

    private class PortalWebViewClient extends WebViewClient {
        private boolean autoSubmitted = false;

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            return !isTrustedPortalUri(uri);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            portalProgress.setVisibility(View.VISIBLE);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            portalProgress.setVisibility(View.GONE);

            if (url.contains("cirm.onlinegla.com") || url.contains("successful")) {
                handleAuthenticationSuccess();
                return;
            }

            if (isTrustedPortalUri(Uri.parse(url)) && !autoSubmitted) {
                portalStatusBanner.setText("Authenticating...");
                injectAutofillScript(view);
            }
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.cancel();
            portalProgress.setVisibility(View.GONE);
            portalStatusBanner.setBackgroundColor(0xFFB71C1C);
            portalStatusBanner.setText("Security Error: Invalid SSL Certificate.");
            Toast.makeText(MainActivity.this, "SSL verification failed. Portal connection blocked for safety.", Toast.LENGTH_LONG).show();
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) {
                portalProgress.setVisibility(View.GONE);
                portalStatusBanner.setBackgroundColor(0xFFB71C1C);
                portalStatusBanner.setText("Network error loading portal.");
            }
        }

        private boolean isTrustedPortalUri(Uri uri) {
            if (uri == null) return false;
            String host = uri.getHost();
            return host != null && (host.equalsIgnoreCase(TRUSTED_HOST) || host.endsWith(".onlinegla.com"));
        }

        private void injectAutofillScript(WebView view) {
            String u = credentialManager.getUsername();
            String p = credentialManager.getPassword();
            if (u == null || p == null) return;

            String safeUser = JSONObject.quote(u);
            String safePass = JSONObject.quote(p);

            String js = "(function() {" +
                    "  var userInput = document.querySelector('input[name=\"username\" i], input[id*=\"user\" i], input[type=\"text\"]');" +
                    "  var passInput = document.querySelector('input[name=\"password\" i], input[id*=\"pass\" i], input[type=\"password\"]');" +
                    "  var submitBtn = document.querySelector('input[type=\"submit\" i], button[type=\"submit\" i], button');" +
                    "  if (userInput && passInput) {" +
                    "    userInput.value = " + safeUser + ";" +
                    "    userInput.dispatchEvent(new Event('input', { bubbles: true }));" +
                    "    userInput.dispatchEvent(new Event('change', { bubbles: true }));" +
                    "    passInput.value = " + safePass + ";" +
                    "    passInput.dispatchEvent(new Event('input', { bubbles: true }));" +
                    "    passInput.dispatchEvent(new Event('change', { bubbles: true }));" +
                    "    if (submitBtn) { submitBtn.click(); return 'SUBMITTED'; }" +
                    "    var form = userInput.form || passInput.form;" +
                    "    if (form) { form.submit(); return 'SUBMITTED'; }" +
                    "  }" +
                    "  return 'FIELDS_NOT_FOUND';" +
                    "})();";

            view.evaluateJavascript(js, result -> {
                if (result != null && result.contains("SUBMITTED")) {
                    autoSubmitted = true;
                    portalStatusBanner.setText("Submitted. Verifying network connection...");
                    view.postDelayed(() -> verifyAuthenticationState(view), 2500);
                }
            });
        }

        private void verifyAuthenticationState(WebView view) {
            String verifyJs = "(function() { return document.body ? document.body.innerText : ''; })();";
            view.evaluateJavascript(verifyJs, text -> {
                if (text != null && (text.contains("successful") || text.contains("Protection profile") || text.contains("logout"))) {
                    handleAuthenticationSuccess();
                }
            });
        }
    }

    private void handleAuthenticationSuccess() {
        if (authenticationDismissed) return;
        authenticationDismissed = true;

        portalStatusBanner.setBackgroundColor(0xFF2E7D32);
        portalStatusBanner.setText("Authentication Successful!");
        Toast.makeText(this, "Signed in to GLA Wi-Fi", Toast.LENGTH_SHORT).show();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && captivePortalObj != null) {
            try {
                captivePortalObj.reportCaptivePortalDismissed();
            } catch (Exception ignored) {
            }
        }

        new Handler(Looper.getMainLooper()).postDelayed(this::finishAndRemoveTask, 1200);
    }

    @Override
    public void onBackPressed() {
        if (layoutPortal.getVisibility() == View.VISIBLE && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}