package org.quanqi.androidnetworkdemo;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;

public class MainActivity extends AppCompatActivity {

    private TextView responseContentTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        responseContentTextView = (TextView) findViewById(R.id.textViewResponseContent);

        StringRequest request = new StringRequest(
                Request.Method.GET,
                "https://api.github.com/repos/vmg/redcarpet/issues?state=closed",
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        responseContentTextView.setText(response);
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        responseContentTextView.setText(error.toString());
                    }
                });
        RequestManager.getInstance(this).addRequest(request, this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        RequestManager.getInstance(this).cancelAll(this);
    }
}
