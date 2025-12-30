package com.group_2.model.cleaning;

import com.group_2.model.WG;
import jakarta.persistence.*;

@Entity
@Table(name = "room", indexes = {
        @Index(name = "idx_room_wg", columnList = "wg_id")
})
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wg_id")
    private WG wg;

    public Room() {
    }

    public Room(String name) {
        this.name = name;
    }

    public Room(String name, WG wg) {
        this.name = name;
        this.wg = wg;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public WG getWg() {
        return wg;
    }

    public void setWg(WG wg) {
        this.wg = wg;
    }
}
