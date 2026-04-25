package com.zpl.handcricket.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Team {
    private Integer id;
    private String code;
    private String name;
    private String landmark;
    private String primaryColor;
}
