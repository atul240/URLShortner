package com.urlshortener.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Serves the browser UI at "/". Assets live under /ui/ (see static/ui). */
@RestController
public class UiController {

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> index() {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(new ClassPathResource("static/index.html"));
    }
}
