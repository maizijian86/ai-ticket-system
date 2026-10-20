package com.aiticket.common.exception;

import com.aiticket.common.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.impl.FileSizeLimitExceededException;
import org.apache.tomcat.util.http.fileupload.impl.SizeLimitExceededException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.error("Business exception: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Map<String, String>> handleValidationException(MethodArgumentNotValidException e) {
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return Result.badRequest("Validation failed: " + errors);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("Unexpected exception", e);
        return Result.error("Internal server error: " + e.getMessage());
    }

    /**
     * 上传体积超限。**这一条必须在 {@link #handleException} 之外单独接住**，否则
     * MultipartException 会掉进兜底分支，用户拿到的是
     * 「Internal server error: Maximum upload size exceeded」—— 英文、说的是内部错误，
     * 而他做的事情只是选了个大文件（实测：11MB 的交付包）。前端虽然也拦一道，但那只
     * 覆盖走对话框的那条路径；换个客户端或改一次限额就漏出来了。
     *
     * 上限不从代码里写死，而是**从异常自己身上读**：每个服务配的数不一样
     * （用户头像和交付物就不是一个量级），写死一个数字必然和某个服务对不上。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        long permitted = permittedBytesOf(e);
        log.error("上传超限: {}", e.getMessage());
        String message = permitted > 0
                ? String.format("文件太大了，单个文件不能超过 %d MB", permitted / 1024 / 1024)
                : "文件太大了，请压缩后再试";
        return Result.error(400, message);
    }

    /** 从异常链里挖出被拒的那个尺寸；挖不到返回 0（调用方据此退化成不带数字的说法）。 */
    private long permittedBytesOf(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof FileSizeLimitExceededException file) {
                return file.getPermittedSize();
            }
            if (t instanceof SizeLimitExceededException request) {
                return request.getPermittedSize();
            }
        }
        return 0;
    }
}
