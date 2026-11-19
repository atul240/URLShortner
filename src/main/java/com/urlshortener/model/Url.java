package com.urlshortener.model;

public class Url {
    public String longUrl;
    public String shortUrl;

    public Url(String longUrl, String shortUrl){
        this.longUrl = longUrl;
        this.shortUrl = shortUrl;
    }
}
