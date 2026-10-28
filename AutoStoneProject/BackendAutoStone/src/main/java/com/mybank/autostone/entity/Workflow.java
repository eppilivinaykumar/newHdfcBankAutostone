//package com.mybank.autostone.entity;
//
//import jakarta.persistence.*;
//import lombok.*;
//
//import java.time.LocalDateTime;
//import java.util.List;
//
//@Entity
//@Table(name = "workflow")
//@Getter
//@Setter
//@NoArgsConstructor
//@AllArgsConstructor
//@Builder
//public class Workflow {
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
//    private Long id;
//    private String name;
//    private String description;
//    private Boolean enabled;
//    private LocalDateTime createdAt;
//    private LocalDateTime updatedAt;
//    @OneToMany(mappedBy = "workflow")
//    private List<Job> jobs;
//}