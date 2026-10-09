package com.example.entity.vo.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

/** 保存头像：传上传接口返回的图片 URL */
@Data
public class AvatarSaveVO {

    @NotBlank(message = "头像地址不能为空")
    @Length(max = 500, message = "头像地址过长")
    String avatar;
}
