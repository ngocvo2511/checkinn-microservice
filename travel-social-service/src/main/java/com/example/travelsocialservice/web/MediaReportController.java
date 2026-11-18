package com.example.travelsocialservice.web;

import com.example.travelsocialservice.security.Actor;
import com.example.travelsocialservice.service.MediaService;
import com.example.travelsocialservice.service.ReportService;
import com.example.travelsocialservice.web.dto.InteractionDtos.MediaUploadView;
import com.example.travelsocialservice.web.dto.InteractionDtos.ReportReceipt;
import com.example.travelsocialservice.web.dto.InteractionDtos.ReportRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/social")
@RequiredArgsConstructor
public class MediaReportController {

    private final MediaService mediaService;
    private final ReportService reportService;

    @PostMapping(value = "/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MediaUploadView> upload(@RequestPart("file") MultipartFile file,
                                                  @AuthenticationPrincipal Jwt jwt) throws IOException {
        Actor actor = Actor.require(jwt);
        return ResponseEntity.status(HttpStatus.CREATED).body(mediaService.upload(actor, file.getBytes()));
    }

    @PostMapping("/reports")
    public ReportReceipt report(@RequestBody(required = false) ReportRequest request, @AuthenticationPrincipal Jwt jwt) {
        reportService.report(Actor.require(jwt), request);
        return new ReportReceipt(true);
    }
}
