package com.tfg.kanban.attachment;

import com.tfg.kanban.activity.ActivityService;
import com.tfg.kanban.attachment.dto.AttachmentResponse;
import com.tfg.kanban.common.ResourceNotFoundException;
import com.tfg.kanban.domain.model.ActivityAction;
import com.tfg.kanban.domain.model.Attachment;
import com.tfg.kanban.domain.model.Task;
import com.tfg.kanban.domain.model.User;
import com.tfg.kanban.domain.repository.AttachmentRepository;
import com.tfg.kanban.domain.repository.UserRepository;
import com.tfg.kanban.security.SecurityUtils;
import com.tfg.kanban.storage.FileStorageService;
import com.tfg.kanban.task.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttachmentService {

    private final AttachmentRepository attachmentRepository;
    private final UserRepository userRepository;
    private final TaskService taskService;
    private final FileStorageService fileStorageService;
    private final ActivityService activityService;

    public List<AttachmentResponse> findByTask(Long taskId) {
        return attachmentRepository.findByTask_IdOrderByCreatedAtAsc(taskId).stream()
                .map(AttachmentResponse::from)
                .toList();
    }

    @Transactional
    public AttachmentResponse upload(Long taskId, MultipartFile file) {
        Task task = taskService.getTask(taskId);
        Long userId = SecurityUtils.requireCurrentUserId();
        User uploader = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", userId));

        String storedName = fileStorageService.store(file);
        String filename = FileStorageService.cleanFilename(file.getOriginalFilename());

        Attachment attachment = attachmentRepository.save(Attachment.builder()
                .task(task)
                .uploadedBy(uploader)
                .filename(filename)
                .storedName(storedName)
                .contentType(file.getContentType())
                .sizeBytes(file.getSize())
                .build());

        activityService.record(task.getBoardColumn().getBoard().getProject(),
                ActivityAction.CREACION, "Attachment", attachment.getId(),
                "Adjunto '%s' a la tarea '%s'".formatted(filename, task.getTitle()));

        return AttachmentResponse.from(attachment);
    }

    public Attachment getAttachment(Long attachmentId) {
        return attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Adjunto", attachmentId));
    }

    public Resource loadContent(Attachment attachment) {
        return fileStorageService.loadAsResource(attachment.getStoredName());
    }

    @Transactional
    public void delete(Long attachmentId) {
        Attachment attachment = getAttachment(attachmentId);
        Task task = attachment.getTask();

        activityService.record(task.getBoardColumn().getBoard().getProject(),
                ActivityAction.BORRADO, "Attachment", attachment.getId(),
                "Borro el adjunto '%s' de la tarea '%s'".formatted(attachment.getFilename(), task.getTitle()));

        attachmentRepository.delete(attachment);
        fileStorageService.delete(attachment.getStoredName());
    }

}
