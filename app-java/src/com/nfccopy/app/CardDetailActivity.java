package com.nfccopy.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import org.json.JSONException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CardDetailActivity extends Activity {

    private String cardId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_card_detail);

        cardId = getIntent().getStringExtra("card_id");
        if (cardId == null) { finish(); return; }

        SharedPreferences prefs = getSharedPreferences("nfc_cards", Context.MODE_PRIVATE);
        String json = prefs.getString("card_" + cardId, null);
        if (json == null) { finish(); return; }

        try {
            JSONObject card = new JSONObject(json);
            TextView detailText = (TextView) findViewById(R.id.detailText);
            final Button btnEmulate = (Button) findViewById(R.id.btnEmulate);

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            StringBuilder sb = new StringBuilder();
            sb.append("이름: ").append(card.optString("name")).append("\n");
            sb.append("UID: ").append(card.optString("uid")).append("\n");
            sb.append("기술: ").append(card.optString("tech")).append("\n");
            String atqa = card.optString("atqa", "");
            if (!atqa.isEmpty()) sb.append("ATQA: ").append(atqa).append("\n");
            String sak = card.optString("sak", "");
            if (!sak.isEmpty()) sb.append("SAK: ").append(sak).append("\n");
            long time = card.optLong("time", 0);
            if (time > 0) sb.append("읽은 시간: ").append(sdf.format(new Date(time))).append("\n");
            String raw = card.optString("raw", "");
            if (!raw.isEmpty()) {
                sb.append("\n").append(raw);
            }
            detailText.setText(sb.toString());

            String activeId = prefs.getString("active_card", null);
            btnEmulate.setText(cardId.equals(activeId) ? "에뮬레이션 중지" : "에뮬레이션 시작");

            btnEmulate.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    SharedPreferences p = getSharedPreferences("nfc_cards", Context.MODE_PRIVATE);
                    String active = p.getString("active_card", null);
                    if (cardId.equals(active)) {
                        p.edit().remove("active_card").apply();
                        btnEmulate.setText("에뮬레이션 시작");
                        Toast.makeText(CardDetailActivity.this, "에뮬레이션 중지", Toast.LENGTH_SHORT).show();
                    } else {
                        p.edit().putString("active_card", cardId).apply();
                        btnEmulate.setText("에뮬레이션 중지");
                        Toast.makeText(CardDetailActivity.this, "에뮬레이션 시작", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        } catch (JSONException e) {
            finish();
        }
    }
}
