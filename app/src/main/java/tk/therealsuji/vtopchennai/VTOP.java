package tk.therealsuji.vtopchennai;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.View;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.json.JSONObject;

public class VTOP {
    Context context;
    WebView webView;
    ImageView captcha;
    Boolean isOpened, isLoggedIn;
    LinearLayout captchaLayout, loadingLayout;
    SharedPreferences sharedPreferences;
    int counter;

    @SuppressLint("SetJavaScriptEnabled")
    public void setVtop(final Context context, WebView webView, ImageView captcha, LinearLayout captchaLayout, LinearLayout loadingLayout, SharedPreferences sharedPreferences) {
        this.context = context;
        this.webView = webView;
        this.captcha = captcha;
        this.captchaLayout = captchaLayout;
        this.loadingLayout = loadingLayout;
        this.sharedPreferences = sharedPreferences;
        this.webView.getSettings().setJavaScriptEnabled(true);
        this.webView.setWebViewClient(new WebViewClient() {
            public void onPageFinished(WebView view, String url) {
                if (!isOpened) {
                    if (counter == 30) {
                        Toast.makeText(context, "Sorry, we had some trouble connecting to the server. Please try again later.", Toast.LENGTH_LONG).show();
                        //Go to previous avtivity
                        return;
                    }
                    isOpened = true;
                    openSignIn();
                    ++counter;
                }
            }
        });

        isOpened = false;
        isLoggedIn = false;
        counter = 1;
        this.webView.loadUrl("http://vtopcc.vit.ac.in:8080/vtop");
    }

    /*
        Function to open the sign in page
     */
    private void openSignIn() {
        webView.evaluateJavascript("(function() {" +
                "var successFlag = false;" +
                "$.ajax({" +
                "type: 'POST'," +
                "url: 'vtopLogin'," +
                "data: null," +
                "async: false," +
                "success: function(response) {" +
                "if(response.search('___INTERNAL___RESPONSE___') == -1 && response.includes('VTOP Login')) {" +
                "$('#page_outline').html(response);" +
                "successFlag = true;" +
                "}" +
                "}" +
                "});" +
                "return successFlag;" +
                "})();", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                if (value.equals("true")) {
                    getCaptcha();
                } else {
                    isOpened = false;
                    reloadPage();
                }
            }
        });
    }

    /*
        Function to reload the page using javascript in case of an error
     */
    public void reloadPage() {
        webView.evaluateJavascript("(function() {" +
                "document.location.href = '/vtop';" +
                "})();", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String src) {

            }
        });
    }

    /*
        Function to get the captcha from the portal's sign in page and load it into the ImageView
     */
    private void getCaptcha() {
        webView.evaluateJavascript("(function() {" +
                "var images = document.getElementsByTagName('img');" +
                "for(var i = 0; i < images.length; ++i) {" +
                "if(images[i].alt == 'vtopCaptcha') {" +
                "return images[i].src;" +
                "}" +
                "}" +
                "})();", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String src) {
                /*
                    src will look like "data:image/png:base64, blablabla...." (including the quotes)
                 */
                if (src.equals("null")) {
                    Toast.makeText(context, "Sorry, something went wrong while trying to fetch the captcha code. Please try again.", Toast.LENGTH_LONG).show();
                    isOpened = false;
                    reloadPage();
                    return;
                }

                src = src.substring(24, src.length() - 1);  //It'll be better (and safer) to split the string using ' ' and take the second value
                byte[] decodedString = Base64.decode(src, Base64.DEFAULT);
                Bitmap decodedImage = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                captcha.setImageBitmap(decodedImage);

                loadingLayout.setVisibility(View.INVISIBLE);
                captchaLayout.setVisibility(View.VISIBLE);
            }
        });
    }

    /*
        Function to sign in to the portal
     */
    public void signIn(String username, String password, String captcha) {
        webView.evaluateJavascript("(function() {" +
                "var credentials = 'uname=" + username + "&passwd=' + encodeURIComponent('" + password + "') + '&captchaCheck=" + captcha + "';" +
                "var successFlag = false;" +
                "$.ajax({" +
                "type : 'POST'," +
                "url : 'doLogin'," +
                "data : credentials," +
                "async: false," +
                "success : function(response) {" +
                "if(response.search('___INTERNAL___RESPONSE___') == -1) {" +
                "if(response.includes('authorizedIDX')) {" +
                "$('#page_outline').html(response);" +
                "successFlag = true;" +
                "} else if(response.includes('Invalid Captcha')) {" +
                "successFlag = 'Invalid Captcha';" +
                "} else if(response.includes('Invalid User Id / Password')) {" +
                "successFlag = 'Invalid User Id / Password';" +
                "} else if(response.includes('User Id Not available')) {" +
                "successFlag = 'User Id Not available';" +
                "}" +
                "}" +
                "}" +
                "});" +
                "return successFlag;" +
                "})();", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                if (value.equals("true")) {
                    isLoggedIn = true;
                    downloadProfile();
                } else {
                    if (!value.equals("false")) {
                        value = value.substring(1, value.length() - 1);
                        if (value.equals("Invalid User Id / Password") || value.equals("User Id Not available")) {
                            sharedPreferences.edit().putString("isLoggedIn", "false").apply();
                            //launch signin activity
                        }
                        Toast.makeText(context, value, Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(context, "Sorry, something went wrong. Please try again.", Toast.LENGTH_LONG).show();
                    }
                    isOpened = false;
                    loadingLayout.setVisibility(View.VISIBLE);
                    captchaLayout.setVisibility(View.INVISIBLE);
                    reloadPage();
                }
            }
        });
    }

    public void downloadProfile() {
        webView.evaluateJavascript("(function() {" +
                "var data = 'verifyMenu=true&winImage=' + $('#winImage').val() + '&authorizedID=' + $('#authorizedIDX').val() + '&nocache=@(new Date().getTime())';" +
                "var obj = false;" +
                "var name = '';" +
                "var id = '';" +
                "var j = 0;" +
                "$.ajax({" +
                "type: 'POST'," +
                "url : 'studentsRecord/StudentProfileAllView'," +
                "data : data," +
                "async: false," +
                "success: function(response) {" +
                "if(response.includes('Personal Information')) {" +
                "var doc = new DOMParser().parseFromString(response, 'text/html');" +
                "var cells = doc.getElementsByTagName('td');" +
                "for(var i = 0; i < cells.length && j < 2; ++i) {" +
                "if(cells[i].innerHTML.includes('Name')) {" +
                "name = cells[++i].innerHTML;" +
                "++j;" +
                "}" +
                "if(cells[i].innerHTML.includes('Register')) {" +
                "id = cells[++i].innerHTML;" +
                "++j;" +
                "}" +
                "}" +
                "obj = {'name': name, 'id': id};" +
                "}" +
                "}" +
                "});" +
                "return obj;" +
                "})();", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String obj) {
                /*
                    obj will look like {"name":"JOHN DOE","register":"20XYZ1987"}
                 */
                if (obj.equals("false")) {
                    Toast.makeText(context, "Sorry, something went wrong. Please try again.", Toast.LENGTH_LONG).show();
                    isOpened = false;
                    loadingLayout.setVisibility(View.VISIBLE);
                    captchaLayout.setVisibility(View.INVISIBLE);
                    reloadPage();
                } else {
                    try {
                        JSONObject myObj = new JSONObject(obj);
                        sharedPreferences.edit().putString("name", myObj.getString("name")).apply();
                        sharedPreferences.edit().putString("id", myObj.getString("id")).apply();
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(context, "Sorry, something went wrong. Please try again later.", Toast.LENGTH_LONG).show();
                    }
                }
            }
        });
    }
}