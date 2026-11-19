package com.urlshortener.encoder;

import org.springframework.stereotype.Component;

@Component
public class Base62Encoder {
    private static final String BASE62 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    public String encode(long num){
        StringBuilder sb = new StringBuilder();
        while(num > 0){
            int rem = (int)(num % 62);
            sb.append(BASE62.charAt(rem));
            num = num / 62;
        }
        return sb.reverse().toString();
    }

    public long decode(String str){
        long num = 0;
        for(int i=0; i < str.length(); i++){
            char c = str.charAt(i);
            int val = BASE62.indexOf(c);
            num = num * 62 + val;
        }
        return num;
    }

}
