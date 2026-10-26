package com.guardianpay.guardianpay.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "guardians")
@Getter @Setter @NoArgsConstructor
public class Guardian {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "protected_user_id", unique = true)
    private User protectedUser;

    @ManyToOne(optional = false)
    @JoinColumn(name = "guardian_user_id")
    private User guardianUser;
}