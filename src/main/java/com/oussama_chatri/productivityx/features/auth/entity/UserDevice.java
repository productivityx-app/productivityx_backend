package com.oussama_chatri.productivityx.features.auth.entity;

import com.oussama_chatri.productivityx.core.enums.Platform;
import com.oussama_chatri.productivityx.core.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_devices",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_user_devices_user_device",
                        columnNames = {"user_id", "device_id"})
        },
        indexes = {
                @Index(name = "idx_user_devices_user_id", columnList = "user_id"),
                @Index(name = "idx_user_devices_last_seen", columnList = "user_id, last_seen_at DESC")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "user_id", insertable = false, updatable = false)
    private UUID userId;

    @Column(name = "device_id", nullable = false, length = 255)
    private String deviceId;

    @Column(name = "device_name", length = 255)
    private String deviceName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Platform platform;

    @Column(name = "push_token", columnDefinition = "TEXT")
    private String pushToken;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}