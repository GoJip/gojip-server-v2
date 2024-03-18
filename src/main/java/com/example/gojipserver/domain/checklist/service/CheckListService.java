package com.example.gojipserver.domain.checklist.service;

import com.example.gojipserver.domain.checklist.dto.*;
import com.example.gojipserver.domain.checklist.entity.CheckList;
import com.example.gojipserver.domain.checklist.entity.cost.ManagementCostOption;
import com.example.gojipserver.domain.checklist.entity.cost.ManagementCostOptionType;
import com.example.gojipserver.domain.checklist.entity.option.InnerOption;
import com.example.gojipserver.domain.checklist.entity.option.InnerOptionType;
import com.example.gojipserver.domain.checklist.entity.option.OuterOption;
import com.example.gojipserver.domain.checklist.entity.option.OuterOptionType;
import com.example.gojipserver.domain.checklist.entity.room.Noise;
import com.example.gojipserver.domain.checklist.entity.room.NoiseType;
import com.example.gojipserver.domain.checklist.entity.room.RoomStatus;
import com.example.gojipserver.domain.checklist.entity.room.RoomStatusType;
import com.example.gojipserver.domain.checklist.repository.*;
import com.example.gojipserver.domain.checklist_collection.entity.CheckListCollection;
import com.example.gojipserver.domain.checklist_collection.repository.CheckListCollectionRepository;
import com.example.gojipserver.domain.collection.entity.Collection;
import com.example.gojipserver.domain.collection.repository.CollectionRepository;
import com.example.gojipserver.domain.roomaddress.entity.RoomAddress;
import com.example.gojipserver.domain.roomaddress.repository.RoomAddressRepository;
import com.example.gojipserver.domain.roomimage.entity.RoomImage;
import com.example.gojipserver.domain.roomimage.repository.RoomImageRepository;
import com.example.gojipserver.domain.user.entity.User;
import com.example.gojipserver.domain.user.repository.UserRepository;
import com.example.gojipserver.global.exception.NotOwnerException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.List;
import java.util.stream.Collectors;


@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CheckListService {

    private final UserRepository userRepository;
    private final RoomImageRepository roomImageRepository;
    private final RoomAddressRepository roomAddressRepository;
    private final ManagementCostOptionRepository managementCostOptionRepository;
    private final NoiseRepository noiseRepository;
    private final RoomStatusRepository roomStatusRepository;
    private final InnerOptionRepository innerOptionRepository;
    private final OuterOptionRepository outerOptionRepository;
    private final CollectionRepository collectionRepository;
    private final CheckListRepository checkListRepository;
    private final CheckListCollectionRepository checkListCollectionRepository;

    @Transactional
    public Long saveCheckList(Long userId, CheckListRequestDto.SaveDto requestDto) {

        // 체크리스트 등록 유저와 주소 세팅
        User findUser = findUserById(userId);
        RoomAddress findRoomAddress = findRoomAddressById(requestDto.getRoomAddressId());

        CheckList savedCheckList = checkListRepository.save(requestDto.toEntity(findUser, findRoomAddress));

        // 양방향 연관관계 세팅
        setManagementCostOptionOfCheckList(savedCheckList, requestDto.getManagementCostOptionTypes());
        setNoiseOfCheckList(savedCheckList, requestDto.getNoiseTypes());
        setRoomStatusOfCheckList(savedCheckList, requestDto.getRoomStatusTypes());
        setInnerOptionOfCheckList(savedCheckList, requestDto.getInnerOptionTypes());
        setOuterOptionOfCheckList(savedCheckList, requestDto.getOuterOptionTypes());
        setRoomImageOfCheckList(savedCheckList, requestDto.getRoomImageIdList());
        setCollectionOfCheckList(savedCheckList, requestDto.getCollectionIdList());

        return savedCheckList.getId();
    }


    @Transactional
    public Long updateCheckList(Long checkListId, Long requestUserId, CheckListRequestDto.UpdateDto requestDto) {
        CheckList findCheckList = findCheckListById(checkListId);

        validCheckListOwner(requestUserId, findCheckList);

        // 체크리스트의 현재 roomImageIdList
        List<Long> currentRoomImageIdList = findCheckList.getRoomImages().stream()
                .map(RoomImage::getId)
                .collect(Collectors.toList());

        // 체크리스트의 현재 collectionIdList
        List<Long> currentCollectionIdList = checkListCollectionRepository.findCollectionIdByCheckListId(findCheckList.getId());

        // 새로 추가할 IdList
        List<Long> requestRoomImageIdList = requestDto.getRoomImageIdList();
        List<Long> requestCollectionIdList = requestDto.getCollectionIdList();

        // 추가해야될 id List와 삭제해야될 id List 가져오기
        List<Long> imageIdListToAdd = getIdListToAdd(currentRoomImageIdList, requestRoomImageIdList);
        List<Long> imageIdListToRemove = getIdListToRemove(currentRoomImageIdList, requestRoomImageIdList);

        List<Long> collectionIdListToAdd = getIdListToAdd(currentCollectionIdList, requestCollectionIdList);
        List<Long> collectionIdListToRemove = getIdListToRemove(currentCollectionIdList, requestCollectionIdList);

        // 삭제
        imageIdListToRemove.stream()
                .forEach(imageIdToRemove -> {
                    roomImageRepository.deleteById(imageIdToRemove);
                });
        collectionIdListToRemove.stream()
                        .forEach(collectionIdToRemove -> {
                            checkListCollectionRepository.deleteByCheckListIdAndCollectionId(findCheckList.getId(), collectionIdToRemove);
                        });
        managementCostOptionRepository.deleteByCheckList(findCheckList);
        noiseRepository.deleteByCheckList(findCheckList);
        roomStatusRepository.deleteByCheckList(findCheckList);
        innerOptionRepository.deleteByCheckList(findCheckList);
        outerOptionRepository.deleteByCheckList(findCheckList);

        setRoomImageOfCheckList(findCheckList, imageIdListToAdd);
        setCollectionOfCheckList(findCheckList, collectionIdListToAdd);
        setManagementCostOptionOfCheckList(findCheckList, requestDto.getManagementCostOptionTypes());
        setNoiseOfCheckList(findCheckList, requestDto.getNoiseTypes());
        setRoomStatusOfCheckList(findCheckList, requestDto.getRoomStatusTypes());
        setInnerOptionOfCheckList(findCheckList, requestDto.getInnerOptionTypes());
        setOuterOptionOfCheckList(findCheckList, requestDto.getOuterOptionTypes());

        findCheckList.update(requestDto);

        return findCheckList.getId();
    }

    private static List<Long> getIdListToRemove(List<Long> currentIdList, List<Long> requestIdList) {
        // 현재 존재하는 엔티티중 삭제해야되는 엔티티의 id
        List<Long> idListToRemove = currentIdList.stream()
                .filter(id -> !requestIdList.contains(id))
                .collect(Collectors.toList());
        return idListToRemove;
    }

    private static List<Long> getIdListToAdd(List<Long> currentIdList, List<Long> requestIdList) {
        // dto로 전달받은 idList중 새로 추가해야할 엔티티의 id
        List<Long> idListToAdd = requestIdList.stream()
                .filter(id -> !currentIdList.contains(id))
                .collect(Collectors.toList());
        return idListToAdd;
    }

    @Transactional
    public void updatePublic(Long requestUserId,Long checkListId) {
        CheckList findCheckList = findCheckListById(checkListId);
        validCheckListOwner(requestUserId, findCheckList);
        findCheckList.updatePublic();
    }


    @Transactional
    public void deleteCheckList(Long requestUserId, Long checkListId) {
        CheckList findCheckList = findCheckListById(checkListId);

        validCheckListOwner(requestUserId, findCheckList);

        checkListRepository.delete(findCheckList);
    }

    public CheckList getCheckListById(Long checkListId) {
        return checkListRepository.findById(checkListId)
                .orElseThrow(() -> new IllegalArgumentException("체크리스트 찾기 실패!, 대상 체크리스트가 존재하지 않습니다. checkListId + " + checkListId));
    }


    // 체크리스트 id를 받아서 RoomAddress 엔티티의 addressName을 얻어오는 코드
    public RoomAddress getRoomAddressByCheckListId(Long checkListId){

        CheckList checkList = checkListRepository.findById(checkListId)
                .orElseThrow(() -> new IllegalArgumentException("CheckListId가 유효하지 않습니다: " + checkListId));

        return checkList.getRoomAddress();
    }

    // user id를 받아서 checkList들의 List를 반환하는 코드
    public List<CheckListAllGetDto> getAllCheckListByUserId(Long userId) {
        List<CheckList> checkLists = checkListRepository.findByUserIdOrderByCreatedDateDesc(userId);

        // 해당 userId에 대한 CheckList가 존재하지 않는 경우
        if (checkLists.isEmpty()) {
            throw new IllegalArgumentException("해당 사용자에 대한 체크리스트가 존재하지 않습니다: " + userId);
        }

        return checkLists.stream().map(checkList -> {
            RoomAddress roomAddress = checkList.getRoomAddress();
            if (roomAddress == null) {
                throw new IllegalStateException("체크리스트에 연결된 RoomAddress가 존재하지 않습니다: " + checkList.getId());
            }
            return new CheckListAllGetDto(checkList, roomAddress);
        }).collect(Collectors.toList());
    }

    // CollectionId를 받아서 collection에 들어있는 checkList들을 반환하는 코드
    public List<CheckListCollectionGetDto> getChecklistsByCollectionId(Long collectionId){
        return checkListRepository.findByCollectionId(collectionId);
    }


    public List<CheckListRecentResponseDto> getRecentCheckListTop3(Long userId) {
        List<CheckList> recentTop3ByUser = findRecentTop3ByUser(userId);
        if (recentTop3ByUser.isEmpty()) {
            throw new IllegalArgumentException("해당 사용자에 대한 체크리스트가 존재하지 않습니다: " + userId);
        }
        return recentTop3ByUser.stream().map(
                checkList -> CheckListRecentResponseDto.builder()
                        .checklistId(checkList.getId())
                        .addressName(checkList.getRoomAddress().getAddressName())
                        .mainImage(null)
                        .build()).collect(Collectors.toList());
    }
    private List<CheckList> findRecentTop3ByUser(Long userId) {
        return checkListRepository.findTop3ByUserIdOrderByLastModifiedDateDesc(userId);
    }

    public List<CheckListCityCountGetDto> getCityCountTop7() {
        return checkListRepository.findCityCountTop7();
    }

    public Page<CheckListCityAllGetDto> getCheckListsByCity(String city, Pageable pageable) {
        Page<CheckListCityAllGetDto> checkListCityAllGetDtos = checkListRepository.findAllCity(city,pageable);
        return checkListCityAllGetDtos;
    }

    private User findUserById(Long userId) {
        User findUser = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 회원이 존재하지 않습니다. userId = " + userId));
        return findUser;
    }

    private RoomAddress findRoomAddressById(Long roomAddressId) {
        RoomAddress findRoomAddress = roomAddressRepository.findById(roomAddressId)
                .orElseThrow(() -> new IllegalArgumentException("해당 주소가 존재하지 않습니다. roomAddressId = " + roomAddressId));
        return findRoomAddress;
    }

    private CheckList findCheckListById(Long checkListId) {
        CheckList findCheckList = checkListRepository.findById(checkListId)
                .orElseThrow(() -> new IllegalArgumentException("해당 체크리스트가 존재하지 않습니다. checkListId = " + checkListId));
        return findCheckList;
    }

    private Collection findCollectionById(Long collectionId) {
        Collection findCollection = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new IllegalArgumentException("해당 컬렉션이 존재하지 않습니다. collectionId = " + collectionId));
        return findCollection;
    }

    private RoomImage findRoomImageById(Long roomImageId) {
        RoomImage findRoomImage = roomImageRepository.findById(roomImageId)
                .orElseThrow(() -> new IllegalArgumentException("해당 이미지가 존재하지 않습니다. roomImageId = " + roomImageId));
        return findRoomImage;
    }

    // CheckList <-> RoomImage 양방향 연관관계 설정
    @Transactional
    public void setRoomImageOfCheckList(CheckList checkList, List<Long> roomImageIdList) {
        if (!roomImageIdList.isEmpty()) {
            for (Long roomImageId : roomImageIdList) {
                RoomImage findRoomImage = findRoomImageById(roomImageId);
                checkList.addRoomImage(findRoomImage);
            }
        }
    }

    // CheckList <-> CheckListCollection, Collection <-> CheckListCollection 양방향 연관관계 설정
    @Transactional
    public void setCollectionOfCheckList(CheckList checkList, List<Long> collectionIdList) {
        if (!collectionIdList.isEmpty()) {
            for (Long collectionId : collectionIdList) {
                Collection findCollection = findCollectionById(collectionId);

                CheckListCollection checkListCollection = CheckListCollection.createCheckListCollection(checkList, findCollection);

                checkList.addCheckListCollection(checkListCollection);
                findCollection.addCheckListCollection(checkListCollection);
            }
        }
    }

    @Transactional
    public void setManagementCostOptionOfCheckList(CheckList checkList, List<ManagementCostOptionType> typeList) {
        if (!typeList.isEmpty()) {
            for (ManagementCostOptionType type : typeList) {
                ManagementCostOption option = ManagementCostOption.builder()
                        .checkList(checkList)
                        .type(type)
                        .build();

                checkList.getManagementCostOptions().add(option);
            }
        }
    }

    @Transactional
    public void setNoiseOfCheckList(CheckList checkList, List<NoiseType> typeList) {
        if (!typeList.isEmpty()) {
            for (NoiseType type : typeList) {
                Noise noise = Noise.builder()
                        .checkList(checkList)
                        .type(type)
                        .build();

                checkList.getNoises().add(noise);
            }
        }
    }

    @Transactional
    public void setRoomStatusOfCheckList(CheckList checkList, List<RoomStatusType> typeList) {
        if (!typeList.isEmpty()) {
            for (RoomStatusType type : typeList) {
                RoomStatus roomStatus = RoomStatus.builder()
                        .checkList(checkList)
                        .type(type)
                        .build();

                checkList.getRoomStatuses().add(roomStatus);
            }
        }
    }

    @Transactional
    public void setInnerOptionOfCheckList(CheckList checkList, List<InnerOptionType> typeList) {
        if (!typeList.isEmpty()) {
            for (InnerOptionType type : typeList) {
                InnerOption innerOption = InnerOption.builder()
                        .checkList(checkList)
                        .type(type)
                        .build();

                checkList.getInnerOptions().add(innerOption);
            }
        }
    }

    @Transactional
    public void setOuterOptionOfCheckList(CheckList checkList, List<OuterOptionType> typeList) {
        if (!typeList.isEmpty()) {
            for (OuterOptionType type : typeList) {
                OuterOption outerOption = OuterOption.builder()
                        .checkList(checkList)
                        .type(type)
                        .build();

                checkList.getOuterOptions().add(outerOption);
            }
        }
    }

    // 삭제 요청을 한 유저가 해당 컬렉션의 소유자가 맞는지 검증
    private static void validCheckListOwner(Long requestUserId, CheckList checkList) {
        if (!checkList.getUser().getId().equals(requestUserId)) {
            throw new NotOwnerException("다른 회원의 체크리스트입니다. checkListId = " + checkList.getId());
        }
    }

}
