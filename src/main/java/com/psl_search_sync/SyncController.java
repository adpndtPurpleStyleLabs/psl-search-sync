package com.psl_search_sync;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@CrossOrigin
public class SyncController {

    @Autowired
    private SyncService service;

    @GetMapping("/fullSync")
    public ResponseEntity<?> fullSync() {
        try {
            service.startFullSync();
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception ex) {
            log.error("Error" + ex.getMessage());
//            ex.printStackTrace();

            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }
}
