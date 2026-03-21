package com.pesaloop.group.adapters.persistence;

import com.pesaloop.group.domain.model.MemberRole;
import com.pesaloop.group.domain.model.MemberStatus;
import com.pesaloop.shared.adapters.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "members")
@Getter
@Setter
public class MemberJpaEntity extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "member_number", nullable = false, length = 20)
    private String memberNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberRole role = MemberRole.MEMBER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberStatus status = MemberStatus.ACTIVE;

    @Column(name = "shares_owned", nullable = false)
    private int sharesOwned = 1;

    @Column(name = "savings_balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal savingsBalance = BigDecimal.ZERO;

    @Column(name = "arrears_balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal arrearsBalance = BigDecimal.ZERO;

    @Column(name = "fines_balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal finesBalance = BigDecimal.ZERO;

    @Column(name = "phone_number", length = 15)
    private String phoneNumber;

    @Column(name = "joined_on", nullable = false)
    private LocalDate joinedOn = LocalDate.now();

    @Column(name = "exited_on")
    private LocalDate exitedOn;

    @Column(name = "next_of_kin_name", length = 100)
    private String nextOfKinName;

    @Column(name = "next_of_kin_phone", length = 15)
    private String nextOfKinPhone;

    @Column(name = "next_of_kin_relationship", length = 50)
    private String nextOfKinRelationship;

    @Column(name = "shares_last_changed_on")
    private LocalDate sharesLastChangedOn;

    @Column(name = "last_login_at")
    private java.time.Instant lastLoginAt;
}
