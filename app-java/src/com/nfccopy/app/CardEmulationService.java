package com.nfccopy.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import org.json.JSONObject;

public class CardEmulationService extends HostApduService {

    private static final String SELECT_APDU_HEADER = "00A40400";
    private static final byte[] SW_OK = {(byte) 0x90, 0x00};
    private static final byte[] SW_NOT_FOUND = {(byte) 0x6A, (byte) 0x82};
    private static final byte[] SW_UNKNOWN = {(byte) 0x6F, 0x00};

    @Override
    public byte[] processCommandApdu(byte[] commandApdu, Bundle extras) {
        String hex = bytesToHex(commandApdu);

        SharedPreferences prefs = getSharedPreferences("nfc_cards", Context.MODE_PRIVATE);
        String activeId = prefs.getString("active_card", null);
        if (activeId == null) return SW_NOT_FOUND;

        String json = prefs.getString("card_" + activeId, null);
        if (json == null) return SW_NOT_FOUND;

        try {
            JSONObject card = new JSONObject(json);
            String uid = card.optString("uid", "");
            if (uid.isEmpty()) return SW_NOT_FOUND;

            byte[] uidBytes = hexToBytes(uid);
            byte[] response = new byte[uidBytes.length + 2];
            System.arraycopy(uidBytes, 0, response, 0, uidBytes.length);
            response[response.length - 2] = (byte) 0x90;
            response[response.length - 1] = 0x00;
            return response;
        } catch (Exception e) {
            return SW_UNKNOWN;
        }
    }

    @Override
    public void onDeactivated(int reason) {
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02X", b));
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        hex = hex.replace(" ", "");
        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }
}
