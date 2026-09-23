package com.tfg.kanban.comment;

import com.tfg.kanban.activity.ActivityService;
import com.tfg.kanban.comment.dto.CommentRequest;
import com.tfg.kanban.comment.dto.CommentResponse;
import com.tfg.kanban.common.PageResponse;
import com.tfg.kanban.common.ResourceNotFoundException;
import com.tfg.kanban.domain.model.ActivityAction;
import com.tfg.kanban.domain.model.Comment;
import com.tfg.kanban.domain.model.Task;
import com.tfg.kanban.domain.model.User;
import com.tfg.kanban.domain.repository.CommentRepository;
import com.tfg.kanban.domain.repository.UserRepository;
import com.tfg.kanban.security.SecurityUtils;
import com.tfg.kanban.task.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final TaskService taskService;
    private final ActivityService activityService;

    public PageResponse<CommentResponse> findByTask(Long taskId, Pageable pageable) {
        return PageResponse.of(commentRepository.findByTask_IdOrderByCreatedAtAsc(taskId, pageable),
                CommentResponse::from);
    }

    @Transactional
    public CommentResponse create(Long taskId, CommentRequest request) {
        Task task = taskService.getTask(taskId);
        Long userId = SecurityUtils.requireCurrentUserId();
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", userId));

        Comment comment = commentRepository.save(Comment.builder()
                .task(task)
                .author(author)
                .content(request.content().trim())
                .build());

        activityService.record(task.getBoardColumn().getBoard().getProject(),
                ActivityAction.COMENTARIO, "Task", task.getId(),
                "Comento en la tarea '%s'".formatted(task.getTitle()));

        return CommentResponse.from(comment);
    }

    @Transactional
    public CommentResponse update(Long commentId, CommentRequest request) {
        Comment comment = getComment(commentId);
        comment.setContent(request.content().trim());
        comment.setEdited(true);
        return CommentResponse.from(comment);
    }

    @Transactional
    public void delete(Long commentId) {
        commentRepository.delete(getComment(commentId));
    }

    private Comment getComment(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comentario", commentId));
    }

}
