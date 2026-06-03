package com.studyhub.aistudyhubbe.controller;

import com.studyhub.aistudyhubbe.dto.ApiResponse;
import com.studyhub.aistudyhubbe.dto.SubjectRequest;
import com.studyhub.aistudyhubbe.dto.SubjectResponse;
import com.studyhub.aistudyhubbe.exception.ApiException;
import com.studyhub.aistudyhubbe.security.UserPrincipal;
import com.studyhub.aistudyhubbe.service.SubjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/subjects")
@Tag(name = "Subjects", description = "Personal subject/category management")
public class SubjectController {

    private final SubjectService subjectService;

    public SubjectController(SubjectService subjectService) {
        this.subjectService = subjectService;
    }

    @Operation(summary = "Create a subject for the current user")
    @PostMapping
    public ApiResponse<SubjectResponse> createSubject(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SubjectRequest request) {
        return ApiResponse.ok("Subject created", subjectService.createSubject(requireUserId(principal), request));
    }

    @Operation(summary = "List subjects for the current user")
    @GetMapping
    public ApiResponse<List<SubjectResponse>> listSubjects(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(subjectService.listSubjects(requireUserId(principal)));
    }

    @Operation(summary = "Update a subject owned by the current user")
    @PatchMapping("/{id}")
    public ApiResponse<SubjectResponse> updateSubject(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody SubjectRequest request) {
        return ApiResponse.ok("Subject updated", subjectService.updateSubject(requireUserId(principal), id, request));
    }

    @Operation(summary = "Delete a subject owned by the current user")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteSubject(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        subjectService.deleteSubject(requireUserId(principal), id);
        return ApiResponse.ok("Subject deleted", null);
    }

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return principal.getId();
    }
}
