package com.example.gojipserver.domain.roomimage.entity;

import com.example.gojipserver.domain.checklist.entity.CheckList;
import com.example.gojipserver.global.auditing.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static jakarta.persistence.FetchType.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomImage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "room_image_id")
    private Long id;

    @Column(nullable = false)
    private String imgUrl;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "check_list_id")
    private CheckList checkList;

    @Builder
    public RoomImage(String imgUrl, CheckList checkList) {
        this.imgUrl = imgUrl;
        this.checkList = checkList;
    }

}
