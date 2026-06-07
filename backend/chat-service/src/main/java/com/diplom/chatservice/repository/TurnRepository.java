package com.diplom.chatservice.repository;

import com.diplom.chatservice.entity.Turn;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TurnRepository extends JpaRepository<Turn, UUID> {

    Page<Turn> findByRoomIdOrderBySeqAsc(UUID roomId, Pageable pageable);

    List<Turn> findByRoomIdOrderBySeqAsc(UUID roomId);

    /**
     * Returns the 50 most recent turns for the given room, ordered by seq descending.
     * The caller should reverse the list to get ascending order.
     * Used by the room-state snapshot on WebSocket subscribe.
     */
    List<Turn> findTop50ByRoomIdOrderBySeqDesc(UUID roomId);
}
