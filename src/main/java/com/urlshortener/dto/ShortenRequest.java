package com.urlshortener.dto;

public class ShortenRequest {
    private String longUrl;
    private String customCode;

    // Getter Setter
    public String getLongUrl() { return longUrl; }
    public void setLongUrl(String longUrl) { this.longUrl = longUrl; }
    public String getCustomCode() { return customCode; }
    public void setCustomCode(String customCode) { this.customCode = customCode; }
}
