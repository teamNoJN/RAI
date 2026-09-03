package com.rai.drug.entity;

import jakarta.persistence.*;
import lombok.*;

/** 국가 마스터. [채팅 시작 ▾] 드롭다운은 이 목록 밖 선택을 차단한다 (screen-02). */
@Entity
@Table(name = "country")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Country {

    @Id
    @Column(name = "country_id", length = 10)
    private String countryId;

    @Column(nullable = false, length = 100)
    private String name;
}
