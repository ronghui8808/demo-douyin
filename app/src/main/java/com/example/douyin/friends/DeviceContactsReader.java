package com.example.douyin.friends;

import android.content.ContentResolver;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DeviceContactsReader {

    private DeviceContactsReader() {
    }

    public static List<ContactEntry> loadNormalizedContacts(ContentResolver resolver) {
        Map<String, String> byPhone = new LinkedHashMap<>();
        if (resolver == null) {
            return new ArrayList<>();
        }

        String[] projection = {
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        };
        try (Cursor cursor = resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                null)) {
            if (cursor == null) {
                return new ArrayList<>();
            }
            int numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
            int nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
            while (cursor.moveToNext()) {
                String raw = numberIdx >= 0 ? cursor.getString(numberIdx) : null;
                String normalized = PhoneNormalizer.normalize(raw);
                if (normalized == null || byPhone.containsKey(normalized)) {
                    continue;
                }
                String name = nameIdx >= 0 ? cursor.getString(nameIdx) : "";
                if (TextUtils.isEmpty(name)) {
                    name = normalized;
                }
                byPhone.put(normalized, name);
            }
        } catch (SecurityException ignored) {
            return new ArrayList<>();
        }

        List<ContactEntry> result = new ArrayList<>(byPhone.size());
        for (Map.Entry<String, String> e : byPhone.entrySet()) {
            result.add(new ContactEntry(e.getKey(), e.getValue()));
        }
        return result;
    }
}
