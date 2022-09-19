package com.ckf.ckfjavafx.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileConfig implements Cloneable, Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 文件名称
     */
    private String name;

    /**
     * 文件路径
     */
    private String path;

    /**
     * 导入时间
     */
    private String importTime;


    @Override
    public FileConfig clone() {
        try {
            return (FileConfig) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
