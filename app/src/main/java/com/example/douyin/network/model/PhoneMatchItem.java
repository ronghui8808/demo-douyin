package com.example.douyin.network.model;

public class PhoneMatchItem {
    public UserDto user;
    public boolean following;
    /** Server-side matched phone for client contact-name join; do not display in UI. */
    public String matchedPhone;
}
