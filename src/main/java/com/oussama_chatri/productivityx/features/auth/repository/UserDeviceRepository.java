package com.oussama_chatri.productivityx.features.auth.repository;

import com.oussama_chatri.productivityx.features.auth.entity.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserDeviceRepository extends JpaRepository<UserDevice, UUID> {

    Optional<UserDevice> findByUserIdAndDeviceId(UUID userId, String deviceId);

    List<UserDevice> findByUserIdOrderByLastSeenAtDesc(UUID userId);

    @Modifying
    @Query("DELETE FROM UserDevice d WHERE d.userId = :userId AND d.deviceId = :deviceId")
    void deleteByUserIdAndDeviceId(@Param("userId") UUID userId, @Param("deviceId") String deviceId);

    @Modifying
    @Query("UPDATE UserDevice d SET d.pushToken = :pushToken WHERE d.userId = :userId AND d.deviceId = :deviceId")
    void updatePushToken(@Param("userId") UUID userId,
                         @Param("deviceId") String deviceId,
                         @Param("pushToken") String pushToken);

    @Modifying
    @Query("UPDATE UserDevice d SET d.lastSeenAt = :now WHERE d.userId = :userId AND d.deviceId = :deviceId")
    void updateLastSeen(@Param("userId") UUID userId,
                        @Param("deviceId") String deviceId,
                        @Param("now") Instant now);

    @Query("SELECT d.pushToken FROM UserDevice d WHERE d.userId = :userId AND d.pushToken IS NOT NULL")
    List<String> findPushTokensByUserId(@Param("userId") UUID userId);
}