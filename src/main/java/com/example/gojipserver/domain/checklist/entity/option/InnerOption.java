package com.example.gojipserver.domain.checklist.entity.option;

import com.example.gojipserver.domain.checklist.entity.CheckList;
import com.example.gojipserver.global.auditing.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class InnerOption extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inner_option_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "check_list_id", nullable = false)
    private CheckList checkList;

    @Column(name = "type")
    private InnerOptionType type;

    @Builder
    public InnerOption(CheckList checkList, InnerOptionType type) {
        this.checkList = checkList;
        this.type = type;
    }
}
