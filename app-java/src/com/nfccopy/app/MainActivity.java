package com.nfccopy.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.*;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

public class MainActivity extends Activity {

    private NfcAdapter nfcAdapter;
    private ListView listView;
    private TextView emptyView;
    private TextView statusText;
    private View readOverlay;
    private FrameLayout fabRead;
    private boolean isReadMode = false;
    private ArrayList<String> cardIds = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        listView = (ListView) findViewById(R.id.listCards);
        emptyView = (TextView) findViewById(R.id.emptyView);
        statusText = (TextView) findViewById(R.id.statusText);
        readOverlay = findViewById(R.id.readOverlay);
        fabRead = (FrameLayout) findViewById(R.id.fabRead);

        setupStatus();
        setupFab();
        setupList();
        handleIntent(getIntent());
    }

    private void setupStatus() {
        if (nfcAdapter == null) {
            statusText.setText("NFC를 지원하지 않는 기기입니다");
        } else if (!nfcAdapter.isEnabled()) {
            statusText.setText("NFC가 비활성화되어 있습니다");
        } else {
            statusText.setText("NFC 준비됨 - 카드를 읽으려면 + 버튼을 누르세요");
        }
    }

    private void setupFab() {
        fabRead.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (nfcAdapter == null || !nfcAdapter.isEnabled()) {
                    Toast.makeText(MainActivity.this, "NFC를 사용할 수 없습니다", Toast.LENGTH_LONG).show();
                    return;
                }
                isReadMode = true;
                readOverlay.setVisibility(View.VISIBLE);
            }
        });
        readOverlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isReadMode = false;
                readOverlay.setVisibility(View.GONE);
            }
        });
    }

    private void setupList() {
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_2, android.R.id.text1, new ArrayList<String>()) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                String cardId = cardIds.get(position);
                try {
                    JSONObject card = getCard(cardId);
                    TextView t1 = (TextView) view.findViewById(android.R.id.text1);
                    TextView t2 = (TextView) view.findViewById(android.R.id.text2);
                    t1.setText(card.optString("name", "Card"));
                    String activeId = getActiveCardId();
                    String status = cardId.equals(activeId) ? " [에뮬레이션 중]" : "";
                    t2.setText("UID: " + card.optString("uid") + " | " + card.optString("tech") + status);
                } catch (Exception e) {
                    // ignore
                }
                return view;
            }
        };
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String cardId = cardIds.get(position);
                Intent intent = new Intent(MainActivity.this, CardDetailActivity.class);
                intent.putExtra("card_id", cardId);
                startActivity(intent);
            }
        });
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, final int position, long id) {
                final String cardId = cardIds.get(position);
                String[] options = {"에뮬레이션 시작/중지", "삭제"};
                new AlertDialog.Builder(MainActivity.this)
                    .setItems(options, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (which == 0) toggleEmulation(cardId);
                            else if (which == 1) deleteCard(cardId);
                        }
                    }).show();
                return true;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        enableForegroundDispatch();
        refreshList();
    }

    @Override
    protected void onPause() {
        super.onPause();
        disableForegroundDispatch();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void enableForegroundDispatch() {
        if (nfcAdapter == null) return;
        Intent intent = new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, 0);
        IntentFilter[] filters = new IntentFilter[]{
            new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED),
            new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
        };
        String[][] techLists = new String[][]{
            new String[]{NfcA.class.getName()},
            new String[]{NfcB.class.getName()},
            new String[]{IsoDep.class.getName()},
            new String[]{MifareClassic.class.getName()},
            new String[]{MifareUltralight.class.getName()},
            new String[]{Ndef.class.getName()}
        };
        nfcAdapter.enableForegroundDispatch(this, pi, filters, techLists);
    }

    private void disableForegroundDispatch() {
        if (nfcAdapter != null) nfcAdapter.disableForegroundDispatch(this);
    }

    private void handleIntent(Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        if (!NfcAdapter.ACTION_TAG_DISCOVERED.equals(action) &&
            !NfcAdapter.ACTION_TECH_DISCOVERED.equals(action) &&
            !NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) return;

        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        if (tag == null) return;
        readTag(tag);
    }

    private void readTag(Tag tag) {
        isReadMode = false;
        readOverlay.setVisibility(View.GONE);

        final String uid = bytesToHex(tag.getId());
        String[] techList = tag.getTechList();
        StringBuilder techStr = new StringBuilder();
        for (String t : techList) {
            if (techStr.length() > 0) techStr.append(", ");
            techStr.append(t.substring(t.lastIndexOf('.') + 1));
        }

        String atqa = "", sak = "";
        StringBuilder rawData = new StringBuilder();

        // NfcA
        NfcA nfcA = NfcA.get(tag);
        if (nfcA != null) {
            atqa = bytesToHex(nfcA.getAtqa());
            sak = String.format("%02X", (int) nfcA.getSak());
        }

        // NDEF
        Ndef ndef = Ndef.get(tag);
        if (ndef != null) {
            try {
                ndef.connect();
                NdefMessage msg = ndef.getNdefMessage();
                if (msg != null) {
                    rawData.append("=== NDEF ===\n");
                    rawData.append("Max Size: ").append(ndef.getMaxSize()).append("\n");
                    rawData.append("Writable: ").append(ndef.isWritable()).append("\n");
                    for (NdefRecord rec : msg.getRecords()) {
                        rawData.append("Record TNF: ").append(rec.getTnf()).append("\n");
                        rawData.append("  Type: ").append(bytesToHex(rec.getType())).append("\n");
                        rawData.append("  Payload: ").append(bytesToHex(rec.getPayload())).append("\n");
                    }
                }
                ndef.close();
            } catch (Exception e) {
                // ignore
            }
        }

        // MifareClassic
        MifareClassic mifare = MifareClassic.get(tag);
        if (mifare != null) {
            try {
                mifare.connect();
                rawData.append("=== MifareClassic ===\n");
                rawData.append("Size: ").append(mifare.getSize()).append(" bytes\n");
                rawData.append("Sectors: ").append(mifare.getSectorCount()).append("\n");
                for (int s = 0; s < mifare.getSectorCount(); s++) {
                    boolean auth = mifare.authenticateSectorWithKeyA(s, MifareClassic.KEY_DEFAULT)
                            || mifare.authenticateSectorWithKeyB(s, MifareClassic.KEY_DEFAULT);
                    if (auth) {
                        int start = mifare.sectorToBlock(s);
                        int count = mifare.getBlockCountInSector(s);
                        for (int b = start; b < start + count; b++) {
                            try {
                                rawData.append("Block ").append(b).append(": ").append(bytesToHex(mifare.readBlock(b))).append("\n");
                            } catch (IOException ex) {
                                rawData.append("Block ").append(b).append(": [error]\n");
                            }
                        }
                    } else {
                        rawData.append("Sector ").append(s).append(": [auth failed]\n");
                    }
                }
                mifare.close();
            } catch (Exception e) {
                // ignore
            }
        }

        // MifareUltralight
        MifareUltralight ul = MifareUltralight.get(tag);
        if (ul != null) {
            try {
                ul.connect();
                rawData.append("=== MifareUltralight ===\n");
                int maxPage = ul.getType() == MifareUltralight.TYPE_ULTRALIGHT_C ? 48 : 16;
                for (int p = 0; p < maxPage; p += 4) {
                    try {
                        rawData.append("Page ").append(p).append(": ").append(bytesToHex(ul.readPages(p))).append("\n");
                    } catch (IOException ex) {
                        break;
                    }
                }
                ul.close();
            } catch (Exception e) {
                // ignore
            }
        }

        // IsoDep
        IsoDep iso = IsoDep.get(tag);
        if (iso != null) {
            try {
                iso.connect();
                rawData.append("=== IsoDep ===\n");
                rawData.append("Max Transceive: ").append(iso.getMaxTransceiveLength()).append("\n");
                if (iso.getHistoricalBytes() != null)
                    rawData.append("Historical: ").append(bytesToHex(iso.getHistoricalBytes())).append("\n");
                iso.close();
            } catch (Exception e) {
                // ignore
            }
        }

        final String fTech = techStr.toString();
        final String fAtqa = atqa;
        final String fSak = sak;
        final String fRaw = rawData.toString();

        // Show save dialog
        final EditText input = new EditText(this);
        input.setHint("카드 이름 입력");
        input.setText("카드 " + uid.substring(Math.max(0, uid.length() - 4)));

        new AlertDialog.Builder(this)
            .setTitle("카드 저장")
            .setMessage("UID: " + uid + "\n기술: " + fTech)
            .setView(input)
            .setPositiveButton("저장", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    String name = input.getText().toString();
                    if (name.isEmpty()) name = "카드 " + uid.substring(Math.max(0, uid.length() - 4));
                    saveCard(uid, name, fTech, fAtqa, fSak, fRaw);
                    refreshList();
                    Toast.makeText(MainActivity.this, "카드가 저장되었습니다", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("취소", null)
            .show();
    }

    // Storage methods
    private SharedPreferences getPrefs() {
        return getSharedPreferences("nfc_cards", Context.MODE_PRIVATE);
    }

    private void saveCard(String uid, String name, String tech, String atqa, String sak, String raw) {
        String id = String.valueOf(System.currentTimeMillis());
        try {
            JSONObject card = new JSONObject();
            card.put("id", id);
            card.put("uid", uid);
            card.put("name", name);
            card.put("tech", tech);
            card.put("atqa", atqa);
            card.put("sak", sak);
            card.put("raw", raw);
            card.put("time", System.currentTimeMillis());
            getPrefs().edit().putString("card_" + id, card.toString()).apply();

            Set<String> ids = new HashSet<>(getPrefs().getStringSet("card_ids", new HashSet<String>()));
            ids.add(id);
            getPrefs().edit().putStringSet("card_ids", ids).apply();
        } catch (JSONException e) {
            // ignore
        }
    }

    private JSONObject getCard(String id) throws JSONException {
        String json = getPrefs().getString("card_" + id, "{}");
        return new JSONObject(json);
    }

    private void deleteCard(String id) {
        getPrefs().edit().remove("card_" + id).apply();
        Set<String> ids = new HashSet<>(getPrefs().getStringSet("card_ids", new HashSet<String>()));
        ids.remove(id);
        getPrefs().edit().putStringSet("card_ids", ids).apply();
        if (id.equals(getActiveCardId())) clearActiveCard();
        refreshList();
    }

    private void toggleEmulation(String id) {
        String activeId = getActiveCardId();
        if (id.equals(activeId)) {
            clearActiveCard();
            Toast.makeText(this, "에뮬레이션 중지", Toast.LENGTH_SHORT).show();
        } else {
            setActiveCard(id);
            try {
                JSONObject card = getCard(id);
                Toast.makeText(this, card.optString("name") + " 에뮬레이션 시작", Toast.LENGTH_SHORT).show();
            } catch (JSONException e) {
                // ignore
            }
        }
        refreshList();
    }

    private void setActiveCard(String id) {
        getPrefs().edit().putString("active_card", id).apply();
    }
    private String getActiveCardId() {
        return getPrefs().getString("active_card", null);
    }
    private void clearActiveCard() {
        getPrefs().edit().remove("active_card").apply();
    }

    private void refreshList() {
        cardIds.clear();
        adapter.clear();
        Set<String> ids = getPrefs().getStringSet("card_ids", new HashSet<String>());
        for (String id : ids) {
            cardIds.add(id);
            try {
                JSONObject card = getCard(id);
                adapter.add(card.optString("name", "Card"));
            } catch (JSONException e) {
                adapter.add("Card");
            }
        }
        adapter.notifyDataSetChanged();
        emptyView.setVisibility(cardIds.isEmpty() ? View.VISIBLE : View.GONE);
        listView.setVisibility(cardIds.isEmpty() ? View.GONE : View.VISIBLE);
    }

    static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02X", b));
        return sb.toString();
    }

    static byte[] hexToBytes(String hex) {
        if (hex == null || hex.isEmpty()) return new byte[0];
        hex = hex.replace(" ", "");
        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }
}
