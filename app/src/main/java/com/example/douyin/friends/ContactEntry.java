package com.example.douyin.friends;

public final class ContactEntry {

    public final String phone;
    public final String displayName;

    public ContactEntry(String phone, String displayName) {
        this.phone = phone;
        this.displayName = displayName != null ? displayName : "";
    }
}
